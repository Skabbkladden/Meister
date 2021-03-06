package edu.buffalo.cse.jive.internal.practql.compiler;

import static edu.buffalo.cse.jive.practql.expression.nary.AdditionConnective.AC_MINUS;
import static edu.buffalo.cse.jive.practql.expression.nary.AdditionConnective.AC_PLUS;
import static edu.buffalo.cse.jive.practql.expression.relational.RelationalConnective.RC_GT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.buffalo.cse.jive.internal.practql.Factory;
import edu.buffalo.cse.jive.internal.practql.visitor.UniformExpressionVisitor;
import edu.buffalo.cse.jive.practql.expression.ExpressionException;
import edu.buffalo.cse.jive.practql.expression.IExpression;
import edu.buffalo.cse.jive.practql.expression.atomic.CAggregateType;
import edu.buffalo.cse.jive.practql.expression.atomic.CFunctionType;
import edu.buffalo.cse.jive.practql.expression.atomic.FunctionType;
import edu.buffalo.cse.jive.practql.expression.atomic.IAggregateExpression;
import edu.buffalo.cse.jive.practql.expression.atomic.IAtomicExpression;
import edu.buffalo.cse.jive.practql.expression.atomic.ICIntervalFieldExpression;
import edu.buffalo.cse.jive.practql.expression.atomic.IFieldExpression;
import edu.buffalo.cse.jive.practql.expression.literal.IIntegerLiteral;
import edu.buffalo.cse.jive.practql.expression.literal.ILiteral;
import edu.buffalo.cse.jive.practql.expression.nary.IAddition;
import edu.buffalo.cse.jive.practql.expression.nary.IConjunction;
import edu.buffalo.cse.jive.practql.expression.query.IClauseFrom;
import edu.buffalo.cse.jive.practql.expression.query.IClauseSelect;
import edu.buffalo.cse.jive.practql.expression.query.IClauseWhere;
import edu.buffalo.cse.jive.practql.expression.query.QueryException;
import edu.buffalo.cse.jive.practql.expression.relational.IRelationalExpression;
import edu.buffalo.cse.jive.practql.expression.relational.RelationalConnective;
import edu.buffalo.cse.jive.practql.expression.unary.INamedExpression;
import edu.buffalo.cse.jive.practql.schema.IFieldReference;
import edu.buffalo.cse.jive.practql.schema.IFieldSchema;
import edu.buffalo.cse.jive.practql.schema.IRelationReference;
import edu.buffalo.cse.jive.practql.schema.SchemaException;
import edu.buffalo.cse.jive.practql.schema.Type;

class SimpleQueryVisitor extends UniformExpressionVisitor
{
  private final Map<IFieldReference, Set<IExpression>> epL;
  private final Map<IFieldReference, Set<IExpression>> epR;
  private final Factory factory;
  private final Map<IFieldReference, Set<IFieldReference>> referenced;
  private IExpression result = null;

  SimpleQueryVisitor(final IClauseFrom from)
  {
    this.referenced = new HashMap<IFieldReference, Set<IFieldReference>>();
    this.epL = new HashMap<IFieldReference, Set<IExpression>>();
    this.epR = new HashMap<IFieldReference, Set<IExpression>>();
    for (int i = 0; i < from.size(); i++)
    {
      final IRelationReference rr = from.getMember(i);
      for (int j = 0; j < rr.size(); j++)
      {
        final IFieldReference fr = rr.getFieldReference(j);
        // if (fr.getSchema().getType() == Type.TP_ENCODED) {
        referenced.put(fr, new HashSet<IFieldReference>());
        epL.put(fr, new HashSet<IExpression>());
        epR.put(fr, new HashSet<IExpression>());
        // }
      }
    }
    this.factory = Factory.INSTANCE;
  }

  private IExpression add(final IExpression e1, final IExpression e2) throws ExpressionException
  {
    if (e1 instanceof ILiteral && e2 instanceof ILiteral)
    {
      return factory.expressionFactory().newInteger(
          ((IIntegerLiteral) e1).getValue() + ((IIntegerLiteral) e2).getValue());
    }
    if (e1 instanceof ILiteral)
    {
      if (((IIntegerLiteral) e1).getValue() == 0)
      {
        return e2;
      }
      if (((IIntegerLiteral) e1).getValue() < 0)
      {
        return factory
            .expressionFactory()
            .newAddition(e2)
            .append(AC_MINUS,
                factory.expressionFactory().newInteger(-((IIntegerLiteral) e1).getValue()));
      }
    }
    else if (e2 instanceof ILiteral)
    {
      if (((IIntegerLiteral) e2).getValue() == 0)
      {
        return e1;
      }
      if (((IIntegerLiteral) e2).getValue() < 0)
      {
        return factory
            .expressionFactory()
            .newAddition(e1)
            .append(AC_MINUS,
                factory.expressionFactory().newInteger(-((IIntegerLiteral) e2).getValue()));
      }
    }
    return factory.expressionFactory().newAddition(e1).append(AC_PLUS, e2);
  }

  private void addLeft(final IFieldExpression fe, final IExpression projected)
  {
    epL.get(fe.getFieldReference()).add(projected);
  }

  private void addRight(final IFieldExpression fe, final IExpression projected)
  {
    epR.get(fe.getFieldReference()).add(projected);
  }

  private IExpression and(final IExpression e1, final IExpression e2) throws ExpressionException
  {
    return factory.expressionFactory().newConjunction(e1).append(e2);
  }

  // compile(t + c = t') <==> compile(t + c >= t') AND compile(t + c <= t')
  private IExpression compileEQ(final IFieldExpression fe1, final IExpression lit,
      final IFieldExpression fe2) throws ExpressionException
  {
    return and(compileLE(fe1, lit, fe2), compileGE(fe1, lit, fe2));
  }

  // compile(t = t') <==> overlapping(t, t')
  private IExpression compileEQ(final IFieldExpression fe1, final IFieldExpression fe2)
      throws ExpressionException
  {
    return and(compileLE(fe1, fe2), compileLE(fe2, fe1));
  }

  /**
   * <pre>
   * Predicate:
   * 
   *   t + c >= t' 
   *   <==> \exits t.l + c <= p < t.r + c AND t'.l <= p' < t'.r AND p >= p'
   *   <==> t.r + c > p AND p' >= t'.l AND p >= p'
   *   <==> t.r + c > p >= p' >= t'.l
   *   <==> t.r + c > p >= t'.l
   *   <==> t.r + c > t'.l
   *   
   * Projection of t:
   * 
   *   t.r + c > t'.l AND t.l < t.r 
   *   <==> [GREATEST(t.l, t.l - c), t'.r)
   * 
   * Projection of t':
   * 
   *   t'.l < t'.r AND t.r + c > t'.l
   *   <==> [t'.l, LEAST(t'.r, t.r + c))
   * 
   * </pre>
   */
  private IExpression compileGE(final IFieldExpression fe1, final IExpression lit,
      final IFieldExpression fe2) throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralGE(fe1, sub(fe2, lit));
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralLE(fe2, add(fe1, lit));
    }
    addLeft(fe1, sub(left(fe2), lit));
    addRight(fe2, add(right(fe1), lit));
    return factory.expressionFactory().newRelationalExpression(add(right(fe1), lit), RC_GT,
        left(fe2));
  }

  /**
   * <pre>
   * Predicate:
   * 
   *   t + c > t' 
   *   <==> \exits t.l + c <= p < t.r + c AND t'.l <= p' < t'.r AND p > p'
   *   <==> t.r + c > p AND p' >= t'.l AND p > p'
   *   <==> t.r + c - 1 >= p > p' >= t'.l
   *   <==> t.r + c - 1 > p' >= t'.l
   *   <==> t.r + c - 1 > t'.l
   *   
   * Projection of t:
   * 
   *   t.r + (c - 1) > t'.l AND t.l < t.r 
   *   <==> [GREATEST(t.l, t.l - (c - 1)), t'.r)
   * 
   * Projection of t':
   * 
   *   t'.l < t'.r AND t.r + (c - 1) > t'.l
   *   <==> [t'.l, LEAST(t'.r, t.r + (c - 1)))
   * 
   * </pre>
   */
  private IExpression compileGT(final IFieldExpression fe1, final IExpression lit,
      final IFieldExpression fe2) throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralGT(fe1, sub(fe2, lit));
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralLT(fe2, add(fe1, lit));
    }
    addLeft(fe1, sub(left(fe2), pred(lit)));
    addRight(fe2, add(right(fe1), pred(lit)));
    return factory.expressionFactory().newRelationalExpression(add(right(fe1), pred(lit)), RC_GT,
        left(fe2));
  }

  /**
   * <pre>
   * Predicate:
   * 
   *   t + c <= t' 
   *   <==> \exits t.l + c <= p < t.r + c AND t'.l <= p' < t'.r AND p <= p'
   *   <==> t.l + c <= p AND p' < t'.r AND p <= p'
   *   <==> t.l + c <= p <= p' < t'.r
   *   <==> t.l + c <= p' < t'.r
   *   <==> t.l < t'.r - c
   *   
   * Projection of t:
   * 
   *   t.l < t'.r - c AND t.l < t.r 
   *   <==> [t.l, LEAST(t.r, t'.r - c))
   * 
   * Projection of t':
   * 
   *   t'.l < t'.r AND t.l < t'.r - c
   *   <==> [GREATEST(t'.l, t.l + c), t'.r)
   * 
   * </pre>
   */
  private IExpression compileLE(final IFieldExpression fe1, final IExpression lit,
      final IFieldExpression fe2) throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralLE(fe1, sub(fe2, lit));
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralGE(fe2, add(fe1, lit));
    }
    addRight(fe1, sub(right(fe2), lit));
    addLeft(fe2, add(left(fe1), lit));
    return factory.expressionFactory().newRelationalExpression(right(fe2), RC_GT,
        add(left(fe1), lit));
  }

  /**
   * <pre>
   * Predicate:
   * 
   *   t <= t' 
   *   <==> \exits t.l <= p < t.r AND t'.l <= p' < t'.r AND p <= p'
   *   <==> t.l <= p AND p' < t'.r AND p <= p'
   *   <==> t.l <= p <= p' < t'.r
   *   <==> t.l <= p' < t'.r
   *   <==> t.l < t'.r
   *   
   * Projection of t:
   * 
   *   t.l < t'.r AND t.l < t.r 
   *   <==> [t.l, LEAST(t.r, t'.r))
   * 
   * Projection of t':
   * 
   *   t'.l < t'.r AND t.l < t'.r
   *   <==> [GREATEST(t'.l, t.l), t'.r)
   * 
   * </pre>
   */
  private IExpression compileLE(final IFieldExpression fe1, final IFieldExpression fe2)
      throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralLE(fe1, fe2);
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralGE(fe2, fe1);
    }
    addRight(fe1, right(fe2));
    addLeft(fe2, left(fe1));
    return factory.expressionFactory().newRelationalExpression(right(fe2), RC_GT, left(fe1));
  }

  private IExpression compileLiteralEQ(final IFieldExpression fe, final IExpression lit)
      throws ExpressionException
  {
    // compile(t = c) <==> compile(t >= c) AND compile(t <= c)
    return and(compileLiteralLE(fe, lit), compileLiteralGE(fe, lit));
  }

  private IExpression compileLiteralGE(final IFieldExpression fe, final IExpression lit)
      throws ExpressionException
  {
    // t --> [GREATEST(t.l, c), t.r)
    addLeft(fe, lit);
    // compile(t >= c) = t.l >= c = has_succi(t, c)
    return factory.expressionFactory().newCFunctionCall(CFunctionType.FT_HAS_SUCC_INCLUSIVE, fe,
        lit);
  }

  private IExpression compileLiteralGT(final IFieldExpression fe, final IExpression lit)
      throws ExpressionException
  {
    // t --> [GREATEST(t.l, c + 1), t.r)
    addLeft(fe, succ(lit));
    // compile(t > c) = t.l > c = has_succ(t, c)
    return factory.expressionFactory().newCFunctionCall(CFunctionType.FT_HAS_SUCC, fe, lit);
  }

  private IExpression compileLiteralLE(final IFieldExpression fe, final IExpression lit)
      throws ExpressionException
  {
    // t --> [t.l, LEAST(t.r, c + 1))
    addRight(fe, succ(lit));
    // compile(t <= c) = t.r <= c + 1 = has_predi(t, c)
    return factory.expressionFactory().newCFunctionCall(CFunctionType.FT_HAS_PRED_INCLUSIVE, fe,
        lit);
  }

  private IExpression compileLiteralLT(final IFieldExpression fe, final IExpression lit)
      throws ExpressionException
  {
    // t --> [t.l, LEAST(t.r, c))
    addRight(fe, lit);
    // compile(t < c) = t.r <= c = has_pred(t, c)
    return factory.expressionFactory().newCFunctionCall(CFunctionType.FT_HAS_PRED, fe, lit);
  }

/**
     * <pre>
     * Predicate:
     * 
     *   t + c < t' 
     *   <==> \exits t.l + c <= p < t.r + c AND t'.l <= p' < t'.r AND p < p'
     *   <==> t.l + c <= p AND p' < t'.r AND p < p'
     *   <==> t.l + c <= p < p' <= t'.r - 1
     *   <==> t.l + c < p' <= t'.r - 1
     *   <==> t.l < t'.r - (c + 1)
     *   
     * Projection of t:
     * 
     *   t.l < t'.r - (c + 1) AND t.l < t.r 
     *   <==> [t.l, LEAST(t.r, t'.r - (c + 1)))
     * 
     * Projection of t':
     * 
     *   t'.l < t'.r AND t.l < t'.r - (c + 1)
     *   <==> [GREATEST(t'.l, t.l + (c + 1)), t'.r)
     *   
     * </pre>
     */
  private IExpression compileLT(final IFieldExpression fe1, final IExpression lit,
      final IFieldExpression fe2) throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralLT(fe1, sub(fe2, lit));
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralGT(fe2, add(fe1, lit));
    }
    addRight(fe1, sub(right(fe2), succ(lit)));
    addLeft(fe2, add(left(fe1), succ(lit)));
    return factory.expressionFactory().newRelationalExpression(right(fe2), RC_GT,
        add(left(fe1), succ(lit)));
  }

/**
     * <pre>
     * Predicate:
     * 
     *   t < t' 
     *   <==> \exits t.l <= p < t.r AND t'.l <= p' < t'.r AND p < p'
     *   <==> t.l <= p AND p' < t'.r AND p < p'
     *   <==> t.l <= p < p' <= t'.r - 1
     *   <==> t.l < p' <= t'.r - 1
     *   <==> t.l < t'.r - 1
     *   
     * Projection of t:
     * 
     *   t.l < t'.r - 1 AND t.l < t.r 
     *   <==> [t.l, LEAST(t.r, t'.r - 1))
     * 
     * Projection of t':
     * 
     *   t'.l < t'.r AND t.l + 1 < t'.r
     *   <==> [GREATEST(t'.l, t.l + 1), t'.r)
     *   
     * </pre>
     */
  private IExpression compileLT(final IFieldExpression fe1, final IFieldExpression fe2)
      throws ExpressionException
  {
    // fe2 behaves like a literal
    if (fe2.getType() == Type.TP)
    {
      return compileLiteralLT(fe1, fe2);
    }
    // fe1 behaves like a literal
    if (fe1.getType() == Type.TP)
    {
      return compileLiteralGT(fe2, fe1);
    }
    addRight(fe1, sub(right(fe2), factory.expressionFactory().newInteger(1)));
    addLeft(fe2, succ(left(fe1)));
    return factory.expressionFactory().newRelationalExpression(right(fe2), RC_GT, succ(left(fe1)));
  }

  private ICIntervalFieldExpression compileToInterval(final IFieldExpression expression)
      throws ExpressionException
  {
    final IFieldReference fr = expression.getFieldReference();
    IFieldSchema fs;
    try
    {
      fs = factory.schemaFactory().newFieldSchema(fr.getSchema().getName(), Type.CINTERVAL);
      final IFieldReference cfr = factory.schemaFactory().newFieldReference(
          fr.getRelationReference(), fs);
      return factory.expressionFactory().newCIntervalFieldExpression(cfr);
    }
    catch (final SchemaException e)
    {
      throw new ExpressionException("Error translating encoded field's left endpoint.", e);
    }
  }

  private IFieldExpression compileToLeftEndpoint(final IFieldExpression expression)
      throws ExpressionException
  {
    final IFieldReference fr = expression.getFieldReference();
    IFieldSchema fs;
    try
    {
      fs = factory.schemaFactory().newFieldSchema(fr.getSchema().getName(), Type.CINTERVAL);
      final IFieldReference cfr = factory.schemaFactory().newFieldReference(
          fr.getRelationReference(), fs);
      final ICIntervalFieldExpression ife = factory.expressionFactory()
          .newCIntervalFieldExpression(cfr);
      return ife.getLeft();
    }
    catch (final SchemaException e)
    {
      throw new ExpressionException("Error translating encoded field's left endpoint.", e);
    }
  }

  private IFieldExpression compileToRightEndpoint(final IFieldExpression expression)
      throws ExpressionException
  {
    final IFieldReference fr = expression.getFieldReference();
    IFieldSchema fs;
    try
    {
      fs = factory.schemaFactory().newFieldSchema(fr.getSchema().getName(), Type.CINTERVAL);
      final IFieldReference cfr = factory.schemaFactory().newFieldReference(
          fr.getRelationReference(), fs);
      final ICIntervalFieldExpression ife = factory.expressionFactory()
          .newCIntervalFieldExpression(cfr);
      return ife.getRight();
    }
    catch (final SchemaException e)
    {
      throw new ExpressionException("Error translating encoded field's left endpoint.", e);
    }
  }

  private IFieldExpression left(final IFieldExpression expression) throws ExpressionException
  {
    return compileToLeftEndpoint(expression);
  }

  private IExpression pred(final IExpression expression) throws ExpressionException
  {
    return sub(expression, factory.expressionFactory().newInteger(1));
  }

  private IFieldExpression right(final IFieldExpression expression) throws ExpressionException
  {
    return compileToRightEndpoint(expression);
  }

  private IExpression sub(final IExpression e1, final IExpression e2) throws ExpressionException
  {
    if (e1 instanceof ILiteral && e2 instanceof ILiteral)
    {
      return factory.expressionFactory().newInteger(
          ((IIntegerLiteral) e1).getValue() + ((IIntegerLiteral) e2).getValue());
    }
    if (e1 instanceof ILiteral)
    {
      if (((IIntegerLiteral) e1).getValue() == 0)
      {
        return e2;
      }
      if (((IIntegerLiteral) e1).getValue() < 0)
      {
        return factory
            .expressionFactory()
            .newAddition(e2)
            .append(AC_PLUS,
                factory.expressionFactory().newInteger(-((IIntegerLiteral) e1).getValue()));
      }
    }
    else if (e2 instanceof ILiteral)
    {
      if (((IIntegerLiteral) e2).getValue() == 0)
      {
        return e1;
      }
      if (((IIntegerLiteral) e2).getValue() < 0)
      {
        return factory
            .expressionFactory()
            .newAddition(e1)
            .append(AC_PLUS,
                factory.expressionFactory().newInteger(-((IIntegerLiteral) e2).getValue()));
      }
    }
    return factory.expressionFactory().newAddition(e1).append(AC_MINUS, e2);
  }

  private IExpression succ(final IExpression expression) throws ExpressionException
  {
    return add(expression, factory.expressionFactory().newInteger(1));
  }

  private IExpression translateCIntervalExpression(final IFieldExpression fe)
      throws ExpressionException
  {
    final List<IExpression> lowerBounds = new ArrayList<IExpression>();
    final List<IExpression> upperBounds = new ArrayList<IExpression>();
    final IFieldReference fr = fe.getFieldReference();
    final ICIntervalFieldExpression ife = compileToInterval(fe);
    // collect lower bounds
    if (!epL.get(fr).isEmpty())
    {
      lowerBounds.add(ife.getLeft());
    }
    for (final IExpression exp : epL.get(fr))
    {
      lowerBounds.add(exp);
    }
    // collect upper bounds
    if (!epR.get(fr).isEmpty())
    {
      upperBounds.add(ife.getRight());
    }
    for (final IExpression exp : epR.get(fr))
    {
      upperBounds.add(exp);
    }
    // no bounds, nothing else to translate
    if (lowerBounds.isEmpty() && upperBounds.isEmpty())
    {
      return ife;
    }
    // construct an interval expression with the lower and upper bounds
    final IExpression lower = lowerBounds.isEmpty() ? ife.getLeft() : factory.expressionFactory()
        .newFunctionCall(FunctionType.FT_GREATEST, lowerBounds);
    final IExpression upper = upperBounds.isEmpty() ? ife.getRight() : factory.expressionFactory()
        .newFunctionCall(FunctionType.FT_LEAST, upperBounds);
    return factory.expressionFactory().newCIntervalExpression(lower, upper);
  }

  @Override
  protected boolean visit(final IExpression expression, final Object arg)
      throws ExpressionException
  {
    if (expression instanceof IAtomicExpression)
    {
      if (result == null)
      {
        result = factory.expressionFactory().newConjunction(expression);
      }
      else
      {
        ((IConjunction) result).append(expression);
      }
      return false;
    }
    if (!(expression instanceof IRelationalExpression))
    {
      return true;
    }
    final IRelationalExpression re = (IRelationalExpression) expression;
    // non-temporal relational expression
    if (re.getLHS().getType() != Type.TP_ENCODED && re.getRHS().getType() != Type.TP_ENCODED)
    {
      if (result == null)
      {
        result = factory.expressionFactory().newConjunction(expression);
      }
      else
      {
        ((IConjunction) result).append(expression);
      }
      return false;
    }
    IExpression compiled = null;
    if (re.getLHS().isLiteral() || re.getRHS().isLiteral())
    {
      final IFieldExpression fe;
      final ILiteral lit;
      if (re.getLHS().isLiteral())
      {
        // c <relop> t
        lit = (ILiteral) re.getLHS();
        fe = (IFieldExpression) re.getRHS();
        switch (re.getConnective())
        {
          case RC_EQ:
            // c = t <==> t = c
            compiled = compileLiteralEQ(fe, lit);
            break;
          case RC_LE:
            // c <= t <==> t >= c
            compiled = compileLiteralGE(fe, lit);
            break;
          case RC_LT:
            // c < t <==> t > c <==> t >= (c + 1)
            compiled = compileLiteralGE(fe, succ(lit));
            break;
        }
      }
      else
      {
        // t <relop> c
        lit = (ILiteral) re.getRHS();
        fe = (IFieldExpression) re.getLHS();
        switch (re.getConnective())
        {
          case RC_EQ:
            // t = c
            compiled = compileLiteralEQ(fe, lit);
            break;
          case RC_LE:
            // t <= c
            compiled = compileLiteralLE(fe, lit);
            break;
          case RC_LT:
            // t < c
            compiled = compileLiteralLT(fe, lit);
            break;
        }
      }
    }
    else
    {
      final IFieldExpression fe1;
      final IFieldExpression fe2;
      final IExpression lit;
      if (re.getLHS() instanceof IAddition)
      {
        // t + c <relop> t'
        final IAddition add = (IAddition) re.getLHS();
        fe1 = (IFieldExpression) (add.getMember(0).getType() == Type.TP_ENCODED ? add.getMember(0)
            : add.getMember(1));
        lit = (add.getMember(0).getType() == Type.TP_ENCODED ? add.getMember(1) : add.getMember(0));
        fe2 = (IFieldExpression) re.getRHS();
        // check dependence
        if (!fe1.equals(fe2) && re.getConnective() != RelationalConnective.RC_EQ)
        {
          referenced.get(fe1.getFieldReference()).add(fe2.getFieldReference());
          referenced.get(fe2.getFieldReference()).add(fe1.getFieldReference());
        }
        switch (re.getConnective())
        {
          case RC_EQ:
            // t + c = t'
            compiled = compileEQ(fe1, lit, fe2);
            break;
          case RC_LE:
            // t + c <= t'
            compiled = compileLE(fe1, lit, fe2);
            break;
          case RC_LT:
            // t + c < t'
            compiled = compileLT(fe1, lit, fe2);
            break;
        }
      }
      else if (re.getRHS() instanceof IAddition)
      {
        // t <relop> t' + c
        fe1 = (IFieldExpression) re.getLHS();
        final IAddition add = (IAddition) re.getRHS();
        fe2 = (IFieldExpression) (add.getMember(0).getType() == Type.TP_ENCODED ? add.getMember(0)
            : add.getMember(1));
        lit = (add.getMember(0).getType() == Type.TP_ENCODED ? add.getMember(1) : add.getMember(0));
        // check dependence
        if (!fe1.equals(fe2) && re.getConnective() != RelationalConnective.RC_EQ)
        {
          referenced.get(fe1.getFieldReference()).add(fe2.getFieldReference());
          referenced.get(fe2.getFieldReference()).add(fe1.getFieldReference());
        }
        switch (re.getConnective())
        {
          case RC_EQ:
            // t = t' + c <==> t' + c = t
            compiled = compileEQ(fe2, lit, fe1);
            break;
          case RC_LE:
            // t <= t' + c <==> t' + c >= t
            compiled = compileGE(fe2, lit, fe1);
            break;
          case RC_LT:
            // t < t' + c <==> t' + c > t <==> t' + c > t
            compiled = compileGT(fe2, lit, fe1);
            break;
        }
      }
      else
      {
        // t <relop> t'
        fe1 = (IFieldExpression) re.getLHS();
        fe2 = (IFieldExpression) re.getRHS();
        // check dependence
        if (!fe1.equals(fe2) && re.getConnective() != RelationalConnective.RC_EQ)
        {
          referenced.get(fe1.getFieldReference()).add(fe2.getFieldReference());
          referenced.get(fe2.getFieldReference()).add(fe1.getFieldReference());
        }
        switch (re.getConnective())
        {
          case RC_EQ:
            // t = t'
            compiled = compileEQ(fe1, fe2);
            break;
          case RC_LE:
            // t <= t'
            compiled = compileLE(fe1, fe2);
            break;
          case RC_LT:
            // t < t'
            compiled = compileLT(fe1, fe2);
            break;
        }
      }
    }
    if (compiled == null)
    {
      throw new ExpressionException(String.format("Unexpected condition compiling '%s'",
          expression.toStringTyped()));
    }
    if (result == null)
    {
      result = factory.expressionFactory().newConjunction(compiled);
    }
    else
    {
      ((IConjunction) result).append(compiled);
    }
    return false;
  }

  Map<IFieldReference, Set<IFieldReference>> getReference()
  {
    return this.referenced;
  }

  IClauseSelect translateSelect(final IClauseSelect select) throws QueryException,
      ExpressionException
  {
    final IClauseSelect newSelect = factory.queryExpressionFactory().newClauseSelect(
        select.isDistinct());
    for (int i = 0; i < select.size(); i++)
    {
      final INamedExpression ne = select.getMember(i);
      // aggregate expression
      if (ne.getExpression() instanceof IAggregateExpression)
      {
        final IAggregateExpression agg = (IAggregateExpression) ne.getExpression();
        // pass-through expression
        if (agg.getArgument().getType() != Type.TP_ENCODED)
        {
          newSelect.append(ne.getName(), ne.getExpression());
        }
        else
        {
          final IExpression carg = translateCIntervalExpression((IFieldExpression) agg
              .getArgument());
          final IExpression cagg = factory.expressionFactory().newCAggregate(
              CAggregateType.getValue(agg.getAggregateType()), carg, agg.isDistinct());
          newSelect.append(ne.getName(), cagg);
        }
      }
      // non-aggregate expression
      else
      {
        // pass-through expression
        if (ne.getExpression().getType() != Type.TP_ENCODED)
        {
          newSelect.append(ne.getName(), ne.getExpression());
        }
        // interval-encoded expression
        else
        {
          newSelect.append(ne.getName(),
              translateCIntervalExpression((IFieldExpression) ne.getExpression()));
        }
      }
    }
    return newSelect;
  }

  IClauseWhere translateWhere(final IClauseWhere where) throws ExpressionException, QueryException
  {
    result = null;
    where.getExpression().accept(this, null);
    if (result instanceof IConjunction && ((IConjunction) result).size() == 1)
    {
      result = ((IConjunction) result).getMember(0);
    }
    return factory.queryExpressionFactory().newClauseWhere(result);
  }
}