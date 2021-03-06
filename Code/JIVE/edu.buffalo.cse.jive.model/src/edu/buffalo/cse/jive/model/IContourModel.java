package edu.buffalo.cse.jive.model;

import java.util.Collection;
import java.util.List;

import edu.buffalo.cse.jive.model.IEventModel.IMethodCallEvent;
import edu.buffalo.cse.jive.model.IStaticModel.IDataNode;
import edu.buffalo.cse.jive.model.IStaticModel.IEnvironmentNode;
import edu.buffalo.cse.jive.model.IStaticModel.IMethodNode;
import edu.buffalo.cse.jive.model.IStaticModel.ITypeNode;

public interface IContourModel extends IModel
{
  public enum ContourKind
  {
    CK_INSTANCE,
    CK_INSTANCE_VIRTUAL,
    CK_METHOD,
    CK_STATIC;
  }

  public interface IContextContour extends IContour
  {
    /**
     * Returns the concrete contour associated with this contour. For static contours this is always
     * the contour itself; for instance contours this is the most specific contour-- i.e., the one
     * for which the program actually invoked {@code new ...}.
     */
    public IContextContour concreteContour();

    public IMethodContour createMethodContour(IMethodNode node, IThreadValue threadId);

    public boolean isStatic();

    /**
     * Flag indicating if this is a virtual contour-- true only if this contour represents the
     * innermost contour of an instance.
     */
    public boolean isVirtual();

    /**
     * Events that initiated executions within the context of this contour.
     */
    public List<IMethodCallEvent> nestedInitiators();

    @Override
    public IContextContour parent();

    /**
     * Description of this contour's structure.
     */
    @Override
    public ITypeNode schema();
  }

  /**
   * Represents the run-time state of a program environment. It may contain members, each of which
   * has a state of its own. It may also be nested within an enclosing environment.
   * <p>
   * The possible types of contours, and their type designation, are:
   * <ul>
   * <li>virtual instance (instance)
   * <li>concrete instance (instance)
   * <li>static (static)
   * <li>static method (method)
   * <li>instance method (method)
   * <li>static inner virtual instance (instance)
   * <li>static inner concrete instance (instance)
   * <li>static inner (static)
   * <li>inner virtual instance (instance)
   * <li>inner concrete instance (instance)
   * </ul>
   * Whether a contour is "inner" or not is revealed by its placement in the contour model.
   */
  public interface IContour extends IModel
  {
    /**
     * Contours nested within this contour. This is a dynamic property in that it may change
     * depending on the state of the execution model.
     */
    public List<IContour> children();

    /**
     * Unique contour identifier.
     */
    public long id();

    /**
     * Kind of this contour.
     */
    public ContourKind kind();

    /**
     * Looks up a member with the given schema within this contour and its enclosing contours. This
     * method can be used for retrieving members from any non-array static and instance contours.
     * For array contours, use the look up based on the member's index.
     */
    public IContourMember lookupMember(IDataNode schema);

    /**
     * Looks up a member with the given index within this contour. This method should be used only
     * for retrieving an array cell member from a particular array contour.
     */
    public IContourMember lookupMember(int index);

    /**
     * Looks up a member with the given name within this contour and its enclosing contours. This
     * method should be used only for retrieving a field member from an instance or static contour.
     * If the contour structure includes shadowed fields with the given name, the field introduced
     * last (i.e., in the most specific contour) is returned.
     */
    public IContourMember lookupMember(String name);

    /**
     * Members of this contour.
     * 
     * @return member instance of this contour
     */
    public Collection<IContourMember> members();

    /**
     * Ordinal identifier of this contour. All contours of a given schema have unique ordinal
     * identifiers.
     */
    public long ordinalId();

    /**
     * Contour that contains this contour. For static and instance contours, this is a reference to
     * the immediate ancestor of this contour. In the case of top-level contours (e.g., an object
     * instance contour), its parent is null. For method contours, this is the execution context
     * associated with the method.
     */
    public IContour parent();

    /**
     * Description of this contour's structure.
     */
    public IEnvironmentNode schema();

    /**
     * String signature of this contour, unique within the execution model.
     */
    public String signature();

    public IContourTokens tokenize();
  }

  /**
   * Run-time realization of a contour member. A member has a schema that minimally provides the
   * name and type of the member. It also has an associated run-time value which, by design, cannot
   * be null. If the actual run-time value is null, {@code value()} should return a NullValue
   * instance; if it is unknown (for instance, because it has not been initialized), then value
   * should return an UninitializedValue instance.
   * 
   * Variable members occur within method contours while field members occur within context
   * contours. These members are associated with the corresponding variable or field schema.
   */
  public interface IContourMember
  {
    public String name();

    public IDataNode schema();

    public IValue value();
  }

  /**
   * Collects information about a contour.
   */
  public interface IContourTokens
  {
    /**
     * Call number of the {@code #methodName} associated with the contour Id.
     */
    public String callNumber();

    /**
     * Fully-qualified class name associated with the contour id.
     */
    public String className();

    /**
     * Instance number associated with the contour Id. This number is unique per class. Returns
     * <code>null</code> if the contour Id references a static contour or a static method contour.
     */
    public String instanceNumber();

    /**
     * Method name associated with the {@code ContourID}, or <code>null</code> if the contour Id
     * references a static or an instance contour.
     */
    public String methodName();
  }

  /**
   * A method contour, representing a method's activation.
   */
  public interface IMethodContour extends IContour
  {
    /**
     * Lookups a variable instance within the members of this contour.
     */
    @Override
    public IContourMember lookupMember(int index);

    /**
     * Lookups a variable instance within the members of this contour.
     */
    public IContourMember lookupMember(final String varName, final int lineNumber);

    public IContourMember lookupResultMember();

    public IContourMember lookupRPDLMember();

    @Override
    public IContextContour parent();

    @Override
    public IMethodNode schema();

    /**
     * Thread in which the activation occurred.
     */
    public IThreadValue thread();
  }

  public interface IObjectContour extends IContextContour
  {
    /**
     * Temporal property that determines whether the contour is garbage collected at the time
     * corresponding to the given eventId.
     */
    public boolean isGarbageCollected(long eventId);

    /**
     * Object contours have a unique object identifier-- more importantly, all contours that make up
     * an object share the same object identifier.
     */
    public long oid();
  }
}
