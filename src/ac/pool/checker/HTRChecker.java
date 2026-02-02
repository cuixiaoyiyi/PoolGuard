












































































































































package ac.pool.checker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ac.pool.PoolMain;
import ac.util.AsyncInherit; 
import destructible.DestructibleIdentify;
import soot.Type;
import soot.AnySubType;
import soot.Body;
import soot.Local;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InvokeExpr;


public class HTRChecker {

	
	public static boolean hasHTRMisuse(RefType type) {
		SootClass sootClass = type.getSootClass();

		
		if (!destructibleSootFieldMap.containsKey(sootClass)
				|| destructibleSootFieldMap.get(sootClass).isEmpty()) {
			return false;
		}

		
		
		
		if (isTransientTaskWrapper(sootClass)) {
			return false;
		}

		return true;
	}

	/**
	 * 【新增方法】判断一个类是否为临时的任务包装器
	 * 特征：
	 * 1. 类名包含 "$"，说明是内部类、匿名类或 Lambda 表达式编译后的合成类。
	 * 2. 该类实现了 Runnable 或 Callable 接口。
	 */
	private static boolean isTransientTaskWrapper(SootClass sootClass) {
		
		
		
		
		if (!sootClass.getName().contains("$")) {
			return false;
		}

		
		
		
		if (AsyncInherit.isInheritedFromRunnable(sootClass)
				|| AsyncInherit.isInheritedFromCallable(sootClass)) {
			return true;
		}

		
        /*
        for (SootClass iface : sootClass.getInterfaces()) {
            if (iface.getName().equals("java.lang.Runnable") ||
                iface.getName().equals("java.util.concurrent.Callable")) {
                return true;
            }
        }
        if (sootClass.hasSuperclass()) {
            String superName = sootClass.getSuperclass().getName();
            
             if (superName.equals("java.util.concurrent.FutureTask")) return true;
        }
        */

		return false;
	}

	
	public final static Map<SootClass, Set<SootField>> destructibleSootFieldMap = new HashMap<>();
	public final static HashSet<SootField> destructibleSootFields = new HashSet<>();

	public static void init() {
		Set<SootClass> sootClasses = new HashSet<SootClass>();
		sootClasses.addAll(Scene.v().getApplicationClasses());
		for (SootClass currentClass : sootClasses) {

			destructibleSootFieldMap.put(currentClass, new HashSet<>());
			reachingDestructibleObject(currentClass);
		}
	}

	
	
	private static void reachingDestructibleObject(SootClass sootClass) {
		
		
		ArrayList<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
		for (SootMethod sootMethod : methods) {
			if (sootMethod.hasActiveBody()) {
				if (PoolMain.isApk() && !Scene.v().getReachableMethods().contains(sootMethod)) {
					continue;
				}
				Body body = sootMethod.getActiveBody();
				for (Unit unit : body.getUnits()) {
					if (unit instanceof DefinitionStmt) {
						DefinitionStmt definitionStmt = (DefinitionStmt) unit;
						if (definitionStmt.getLeftOp() instanceof FieldRef)
						{
							FieldRef fieldRef = (FieldRef) definitionStmt.getLeftOp();
							if (fieldRef.getField() != null && fieldRef.getField().isStatic()) {
								continue;
							}
							if (fieldRef.getField().getDeclaringClass().getName().equals(sootClass.getName()))
							{
								Value rightOp = definitionStmt.getRightOp();
								PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
								PointsToSet pointsToSet = null;
								Set<Type> types = new HashSet<>();
								if (rightOp instanceof Local) {
									pointsToSet = pointsToAnalysis.reachingObjects((Local) rightOp);
								}
								else if (rightOp instanceof FieldRef)
								{
									FieldRef fieldRef2 = (FieldRef) rightOp;
									if (fieldRef2.getField() != null && fieldRef2.getField().isStatic()) {
										pointsToSet = pointsToAnalysis.reachingObjects(fieldRef2.getField());
									}
								}
								else if (rightOp instanceof InvokeExpr) {
									InvokeExpr invokeExpr = (InvokeExpr) rightOp;
									types.add(invokeExpr.getMethod().getReturnType());
								}
								if (pointsToSet != null && pointsToSet.possibleTypes() != null) {
									types.addAll(pointsToSet.possibleTypes());
								}
								Set<RefType> refTypes = new HashSet<>();
								for (Type type : types) {
									if (type instanceof RefType) {
										refTypes.add((RefType) type);
									}
									else if (type instanceof AnySubType)
									{
										refTypes.add(((AnySubType) type).getBase());
									}
								}
								for (RefType refType : refTypes) {
									SootClass fieldClass = refType.getSootClass();
									if (DestructibleIdentify.isDestructibleClass(fieldClass, fieldClass)) {
										destructibleSootFieldMap.get(sootClass).add(fieldRef.getField());
										destructibleSootFields.add(fieldRef.getField());
									}
								}

							}
						}
					}
				}
			}
		}
	}
}