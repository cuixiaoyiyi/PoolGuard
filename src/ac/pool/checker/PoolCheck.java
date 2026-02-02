package ac.pool.checker;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soot.jimple.*;
import soot.*;
import ac.component.PointCollectorExecutor;
import ac.pool.ThreadErrorRecord;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import ac.pool.point.OneParaValueKeyPoint;
import ac.pool.point.PointCollector;
import ac.util.Log;
import soot.jimple.toolkits.annotation.logic.Loop;

import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.LoopNestTree;
import soot.toolkits.graph.UnitGraph;


public class PoolCheck implements ICheck {
	public static final ExecutorService executor = Executors
			.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2);
	
	protected PointCollector pointCollector = null;
	
	protected String component = null;
	
	protected Set<String> iNRClasses = null;

	public PoolCheck(PointCollector pointCollector, String component, Set<String> iNRClasses) {
		this.pointCollector = pointCollector;
		this.component = component;
		this.iNRClasses = iNRClasses;
	}
	/**
	 * 增强版逃逸分析：支持局部变量传递追踪
	 * 解决：$r31 (allocation) -> dtpExecutor (local var) -> return dtpExecutor 这种链路
	 */
	private static boolean checkEscape(SootMethod method, Local allocatedLocal) {
		if (!method.hasActiveBody()) return false;

		
		Set<Value> trackedValues = new HashSet<>();
		trackedValues.add(allocatedLocal);

		
		
		
		
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Unit unit : method.getActiveBody().getUnits()) {
				if (unit instanceof AssignStmt) {
					AssignStmt assign = (AssignStmt) unit;
					Value left = assign.getLeftOp();
					Value right = assign.getRightOp();

					
					if (trackedValues.contains(right)) {
						if (trackedValues.add(left)) {
							changed = true;
						}
					}

					
					if (right instanceof CastExpr) {
						Value castOp = ((CastExpr) right).getOp();
						if (trackedValues.contains(castOp)) {
							if (trackedValues.add(left)) {
								changed = true;
							}
						}
					}
				}
			}
		}

		
		for (Unit unit : method.getActiveBody().getUnits()) {

			
			if (unit instanceof ReturnStmt) {
				ReturnStmt returnStmt = (ReturnStmt) unit;
				if (trackedValues.contains(returnStmt.getOp())) {
					return true; 
				}
			}

			
			if (unit instanceof AssignStmt) {
				AssignStmt assignStmt = (AssignStmt) unit;
				if (assignStmt.getLeftOp() instanceof FieldRef) {
					if (trackedValues.contains(assignStmt.getRightOp())) {
						return true;
					}
				}
			}

			
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					
					for (Value arg : invokeExpr.getArgs()) {
						if (trackedValues.contains(arg)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	private static boolean isReachable(UnitGraph graph, Unit startUnit, Unit endUnit) {
		if (startUnit == endUnit) return true;

		Queue<Unit> queue = new LinkedList<>();
		Set<Unit> visited = new HashSet<>();

		
		queue.add(startUnit);
		visited.add(startUnit);

		while (!queue.isEmpty()) {
			Unit current = queue.poll();

			
			for (Unit succ : graph.getSuccsOf(current)) {
				
				if (succ == endUnit) {
					return true;
				}

				
				if (!visited.contains(succ)) {
					visited.add(succ);
					queue.add(succ);
				}
			}
		}

		
		return false;
	}

	private static boolean isStmtInLoop(SootMethod method, Unit stmt) {
		
		if (!method.hasActiveBody()) {
			return false;
		}

		try {
			Body body = method.getActiveBody();

			
			
			
			LoopNestTree loopNestTree = new LoopNestTree(body);
			

			
			for (Loop loop : loopNestTree) {
				
				
				if (loop.getLoopStatements().contains(stmt)) {
					return true;
				}
			}
		} catch (Exception e) {
			
			
		}

		return false;
	}
	private static boolean isSameMethodAndVariable(InitPoint initPoint, Value initVar, KeyPoint terminatePoint) {
		
		if (initPoint.getMethod() != terminatePoint.getMethod()) {
			return false;
		}

		
		Value shutdownVar = null;
		Unit termUnit = terminatePoint.getStmt();
		if (termUnit instanceof Stmt) {
			Stmt stmt = (Stmt) termUnit;
			if (stmt.containsInvokeExpr()) {
				InvokeExpr expr = stmt.getInvokeExpr();
				
				if (expr instanceof InstanceInvokeExpr) {
					shutdownVar = ((InstanceInvokeExpr) expr).getBase();
				}
			}
		}

		
		
		if (shutdownVar != null && initVar.equals(shutdownVar)) {
			return true;
		}

		return false;
	}
	private boolean isReturned(SootMethod method, Value targetVar) {
		if (!method.hasActiveBody()) {
			return false;
		}

		
		for (Unit unit : method.getActiveBody().getUnits()) {
			
			if (unit instanceof ReturnStmt) {
				ReturnStmt returnStmt = (ReturnStmt) unit;
				Value returnValue = returnStmt.getOp();

				
				if (returnValue.equals(targetVar)) {
					return true;
				}

				
				
				
			}
		}
		return false;
	}


	@Override
	public void check() {
		if (pointCollector.getStartPoints().isEmpty()) {
			Log.i(component, " # end: StartPoint set is empty..  ");
			return;
		}
		Log.i(component, " # InitialPoints Size ", pointCollector.getInitialPoints().size());
		Log.i(component, " # StartPoints Size ", pointCollector.getStartPoints().size());

		Log.i(component, " # 1. Start HTR..  ");

		for (InitPoint point : pointCollector.getInitialPoints()) {

			HTRLoop: for (OneParaKeyPoint submitPoint : pointCollector.getSubmitPoints()) {
				if (!point.isAliasCaller(submitPoint)) {

					continue;
				}

				for (RefType refType : submitPoint.getParaLocalPossiableTypes()) {

					if (HTRChecker.hasHTRMisuse(refType)) {
						ThreadErrorRecord.recordHTR(component, point, refType.getSootClass());
						break HTRLoop;
					}
				}
			}
		}



		Log.i(component, " # 2. Start INR..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			INRLoop: for (OneParaKeyPoint submitPoint : pointCollector.getSubmitPoints()) {
				if (!point.isAliasCaller(submitPoint)) {
					continue;
				}
				for (KeyPoint shutDownNowPoint : pointCollector.getShutDownNowPoints()) {
					if (!shutDownNowPoint.isAliasCaller(point)) {
						continue;
					}
					for (RefType refType : submitPoint.getParaLocalPossiableTypes()) {
						if (iNRClasses.contains(refType.toString())) {
							ThreadErrorRecord.recordINR(component, point, refType.getSootClass());
							break INRLoop;
						}
					}
				}
			}
		}

		Log.i(component, " # 3. Start NTT..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			for (OneParaKeyPoint startPoint : pointCollector.getSubmitPoints()) {
				if (!startPoint.isAliasCaller(point)) {
					continue;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
							keyPoints.addAll(pointCollector.getShutDownNowPoints());
							keyPoints.addAll(pointCollector.getShutDownPoints());
							if (NTTChecker.checkNTTMisuse(startPoint, keyPoints, pointCollector)) {
								ThreadErrorRecord.recordNTT(component, point);
							}
						} catch (Throwable e) {
							Log.i("## Exception During NTT ##", e.getClass());
						}

					}
				});
			}
		}






































































































		

















































































		Log.i(component, " # 3.1. Start NT..  ");

		for (InitPoint point : pointCollector.getInitialPoints()) {

			
			SootMethod method = point.getMethod();
			String methodName = method.getName();
			SootClass declaringClass = method.getDeclaringClass();
			String className = declaringClass.getName();

			boolean isLambdaContext =
					className.contains("lambda") ||
							className.contains("Lambda") ||
							methodName.contains("lambda$") ||
							(className.contains("$") && (methodName.equals("apply") || methodName.equals("run")));

			boolean isAllocation = false;
			Local allocatedLocal = null;
			InvokeExpr originInvokeExpr = null; 

			
			if (point.getStmt() instanceof DefinitionStmt) {
				DefinitionStmt stmt = (DefinitionStmt) point.getStmt();
				if (stmt.getRightOp() instanceof NewExpr || stmt.containsInvokeExpr()) {
					isAllocation = stmt.getRightOp() instanceof NewExpr;
					if (stmt.getLeftOp() instanceof Local) {
						allocatedLocal = (Local) stmt.getLeftOp();
					}
					
					if (stmt.containsInvokeExpr()) {
						originInvokeExpr = stmt.getInvokeExpr();
					}
				}
			} else if (point.getStmt() instanceof Stmt && ((Stmt)point.getStmt()).containsInvokeExpr()) {
				InvokeExpr expr = ((Stmt)point.getStmt()).getInvokeExpr();
				if (expr instanceof InstanceInvokeExpr && expr.getMethod().getName().equals("<init>")) {
					isAllocation = true;
					Value base = ((InstanceInvokeExpr) expr).getBase();
					if (base instanceof Local) {
						allocatedLocal = (Local) base;
					}
				}
			}

			
			if (isLambdaContext && !isAllocation) {
				continue;
			}

			
			
			
			if (!isAllocation && originInvokeExpr != null) {
				String invokedMethodName = originInvokeExpr.getMethod().getName();

				
				
				if (invokedMethodName.startsWith("access$")) {
					continue;
				}

				
				
				if (invokedMethodName.startsWith("get") ||
						invokedMethodName.startsWith("current") ||
						invokedMethodName.startsWith("find") ||
						invokedMethodName.startsWith("lookup") ||
						invokedMethodName.equals("executor")) { 

					
					
					continue;
				}

				
				if (invokedMethodName.equals("getInstance")) {
					continue;
				}
			}

			
			
			if (allocatedLocal != null) {
				
				if (checkEscape(method, allocatedLocal)) {
					continue; 
				}
			}

			
			Set<KeyPoint> keyPoints = new HashSet<KeyPoint>();
			keyPoints.addAll(pointCollector.getShutDownNowPoints());
			keyPoints.addAll(pointCollector.getShutDownPoints());

			boolean terminate = false;
			for (KeyPoint terminatePoint : keyPoints) {
				
				if (point.isAliasCaller(terminatePoint)) {
					terminate = true;
					break;
				}

				
				if (allocatedLocal != null) {
					if (isSameMethodAndVariable(point, allocatedLocal, terminatePoint)) {
						terminate = true;
						break;
					}
				}
			}

			if (!terminate) {
				ThreadErrorRecord.recordNT(component, point);
			}
		}











		Log.i(component, " # 4. Start IL..  ");

		for (KeyPoint point : pointCollector.getIsTerminatedPoints()) {
			if (ILChecker.isShutDownMisuse(pointCollector.getShutDownPoints(), point)) {
				ThreadErrorRecord.recordIL(component, point);
			}

		}











		Log.i(component, " # 5. Start CallerRunsChecker..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {

			if (CallerRunsChecker.hasMisuse(point, pointCollector.getSetRejectedExecutionHandlerPoints())) {
				ThreadErrorRecord.recordCallerRunsChecker(component, point);
			}
		}























		Log.i(component, " # 6. Start ExceptionHandlerChecker (Scope Based)..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {
			
			
			boolean hasMisuse = ScopeExceptionHandlerChecker.hasMisuse(
					point,
					pointCollector.getSetThreadFactoryPoints(),
					pointCollector.getSetUncaughtExceptionHandlerPoints()
			);

			if (hasMisuse) {
				ThreadErrorRecord.recordNonExceptionHandlerChecker(component, point);
			}
		}















		Log.i(component, " # 7. Start RepeatedlyCreateThreadPool (Loop & Path Reachability)..  ");


		Map<SootMethod, List<InitPoint>> methodCreationCounts = new HashMap<>();


		for (InitPoint point : pointCollector.getInitialPoints()) {
			SootMethod method = point.getMethod();

			
			
			
			if (!methodCreationCounts.containsKey(method)) {
				methodCreationCounts.put(method, new ArrayList<InitPoint>());
			}
			methodCreationCounts.get(method).add(point);

			
			
			
			if (isStmtInLoop(method, point.getStmt())) {
				Log.i(component, "RCTP found in Loop: " + method.getSignature());
				ThreadErrorRecord.recordRCTP(component, point);
			}
		}





		for (Map.Entry<SootMethod, List<InitPoint>> entry : methodCreationCounts.entrySet()) {
			List<InitPoint> points = entry.getValue();

			
			if (points.size() < 2) continue;

			SootMethod method = entry.getKey();
			if (!method.hasActiveBody()) continue;

			
			BriefUnitGraph cfg = new BriefUnitGraph(method.getActiveBody());

			
			Set<InitPoint> truePositivePoints = new HashSet<>();

			
			for (int i = 0; i < points.size(); i++) {
				for (int j = 0; j < points.size(); j++) {
					if (i == j) continue; 

					InitPoint pointA = points.get(i);
					InitPoint pointB = points.get(j);

					
					
					if (isReachable(cfg, pointA.getStmt(), pointB.getStmt())) {
						truePositivePoints.add(pointA);
						truePositivePoints.add(pointB);
					}
				}
			}

			
			if (!truePositivePoints.isEmpty()) {
				Log.i(component, "RCTP found (Sequential creations): " + method.getSignature());
				for (InitPoint point : truePositivePoints) {
					ThreadErrorRecord.recordRCTP(component, point);
				}
			} else {
				
				Log.i(component, "Ignored Mutually Exclusive Creations (Factory Pattern): " + method.getSignature());
			}
		}






























































		Log.i(component, " # 8. Start UnrefactoredThreadLocal (UTL)..  ");
		
		
		for (InitPoint point : pointCollector.getInitialPoints()) {
			
			if (ScopeThreadLocalChecker.hasThreadLocalMisuse(point)) {
				ThreadErrorRecord.recordUTL(component, point);
				
				
			}
		}



		Log.i(component, " # 9. Start UnboundedNumberOfThread (UBNT Core)..  ");
		if (pointCollector instanceof PointCollectorExecutor) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				for (OneParaValueKeyPoint setSizePoint : pointCollector.getSetCoreThreadSizePoints()) {
					if (!setSizePoint.isAliasCaller(point)) {
						continue;
					}
					if (IntMaxChecker.hasMaxIntegerSizeMisuse(setSizePoint)) {
						ThreadErrorRecord.recordUBNT(component , point);
						break;
					}
				}
			}
		}
		Log.i(component, " # 10. Start UnboundedNumberOfThread (UBNT Max)..  ", pointCollector.getSetMaxThreadSizePoints());
		if (pointCollector instanceof PointCollectorExecutor) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				for (OneParaValueKeyPoint setSizePoint : pointCollector.getSetMaxThreadSizePoints()) {
					if (!setSizePoint.isAliasCaller(point)) {
						continue;
					}
					if (IntMaxChecker.hasMaxIntegerSizeMisuse(setSizePoint)) {
						ThreadErrorRecord.recordUBNT(component , point);
						break;
					}
				}
			}
		}











		Log.i(component, " # 11. Start UnnamedThread (UNT) (Scope Based)..  ");
		for (InitPoint point : pointCollector.getInitialPoints()) {

			if (ScopeThreadNameChecker.hasMisuse(point)) {
				ThreadErrorRecord.recordUNT(component, point);
				break;
			}
		}




	}

}
