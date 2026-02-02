package ac.pool.point;

import java.util.HashSet;
import java.util.Set;

import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.spark.sets.EmptyPointsToSet;
import soot.jimple.toolkits.pointer.FullObjectSet;





public class KeyPoint {
	
	public static final PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
	SootMethod method = null;
	Stmt stmt = null; 
	private Value caller = null;




	protected Value getParameter(int index) {
		return stmt.getInvokeExpr().getArg(index);
	}

	protected PointsToSet reachingObjects(Value value) {
		if(value instanceof Local) {
			return  pta.reachingObjects((Local) value);
		}
		return EmptyPointsToSet.v();
	}


	public PointsToSet getCallerPointsToSet() {
		return reachingObjects(getCaller());
	}

	public Set<RefType> getCallerPossibleType() {
		PointsToSet pts = getCallerPointsToSet();
		Set<RefType> set = new HashSet<RefType>();
		for(Type type: pts.possibleTypes()) {
			if(type instanceof RefType) {
				set.add((RefType) type);
			}
		}
		return set ;
	}






	public boolean isAliasCaller(KeyPoint otherKeyPoint) {

		PointsToSet thisPTS = getCallerPointsToSet();
		PointsToSet otherPTS = otherKeyPoint.getCallerPointsToSet();


		if (thisPTS instanceof FullObjectSet || otherPTS instanceof FullObjectSet) {
			
			Value thisCaller = this.getCaller();
			Value otherCaller = otherKeyPoint.getCaller();
			if (thisCaller instanceof Local && otherCaller instanceof Local) {
				Local thisLocal = (Local) thisCaller;
				Local otherLocal = (Local) otherCaller;
				
				return thisLocal.getName().equals(otherLocal.getName());
			}
			return false; 
		}
		
		boolean result = thisPTS.hasNonEmptyIntersection(otherPTS);
		return result;
	}

	
	public boolean isAliasCaller(Local local) {
		return getCallerPointsToSet().hasNonEmptyIntersection(pta.reachingObjects(local));
	}



	public static KeyPoint newPoint(SootMethod method, Stmt stmt) {
		KeyPoint keyPoint = new KeyPoint();
		keyPoint.method = method;
		keyPoint.stmt = stmt;
		keyPoint.setCaller(getBaseCaller(stmt));
		return keyPoint;
	}
	
	public static Value getBaseCaller(Stmt stmt) {
		return getBaseCaller(stmt.getInvokeExpr());
	}
	
	public static Value getBaseCaller(InvokeExpr invokeExpr) {
		if(invokeExpr instanceof InstanceInvokeExpr) {
			InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;
			return instanceInvokeExpr.getBase();
		}
		return null;
	}







	public SootMethod getMethod() {
		return method;
	}

	public void setMethod(SootMethod method) {
		this.method = method;
	}

	public void setStmt(Stmt stmt) {
		this.stmt = stmt;
	}
	public Stmt getStmt() {
		return stmt;
	}
	public Value getCaller() {
		return caller;
	}

	public void setCaller(Value caller) {
		this.caller = caller;
	}

}