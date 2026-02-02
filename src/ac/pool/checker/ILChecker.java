package ac.pool.checker;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

import soot.*;
import soot.jimple.IfStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;   
import soot.jimple.ReturnVoidStmt;
import soot.jimple.ThrowStmt;    
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.toolkits.graph.BriefUnitGraph;
import ac.pool.point.KeyPoint;

public class ILChecker {

	
	public static boolean isShutDownMisuse(Set<KeyPoint> shutDowns, KeyPoint isShutDownPoint) {

		
		LoopConditionAnalysis analysis = new LoopConditionAnalysis(isShutDownPoint);
		if (!analysis.isShutDownLoopCondition) {
			return false;
		}

		
		
		
		if (analysis.leadsToExitOrThrow) {
			return false;
		}

		
		for (KeyPoint shutDown : shutDowns) {

			
			if (isSameMethodAndLocal(isShutDownPoint, shutDown)) {
				return false;
			}

			
			if (isShutDownPoint.isAliasCaller(shutDown)) {
				return false;
			}

			
			if (isLambdaAlias(isShutDownPoint, shutDown)) {
				return false;
			}
		}

		
		return true;
	}

	/**
	 * [新增] 快速检查两个点是否在同一个方法内，且使用同一个 Local 变量
	 * 这比复杂的指针分析更准、更快，专门解决 AgentRunner 这类局部逻辑
	 */
	private static boolean isSameMethodAndLocal(KeyPoint p1, KeyPoint p2) {
		if (!p1.getMethod().equals(p2.getMethod())) {
			return false;
		}
		Value v1 = p1.getCaller();
		Value v2 = p2.getCaller();
		
		return v1 != null && v2 != null && v1.equals(v2);
	}

	private static boolean isLambdaAlias(KeyPoint lambdaPoint, KeyPoint outerPoint) {
		
		SootMethod lambdaMethod = lambdaPoint.getMethod();

		if (!lambdaMethod.getName().contains("lambda") && !lambdaMethod.getName().contains("$")) {
			return false;
		}

		Value outerCaller = outerPoint.getCaller();
		Value lambdaCaller = lambdaPoint.getCaller();

		if (!lambdaMethod.hasActiveBody()) return false;

		for (Unit u : lambdaMethod.getActiveBody().getUnits()) {
			if (u instanceof DefinitionStmt) {
				DefinitionStmt def = (DefinitionStmt) u;
				if (def.getLeftOp().equals(lambdaCaller)) {
					Value rightOp = def.getRightOp();

					if (rightOp instanceof FieldRef) {
						if (outerCaller instanceof Local) {
							String outerName = ((Local) outerCaller).getName();
							String fieldName = ((FieldRef) rightOp).getField().getName();
							if (fieldName.toLowerCase().contains(outerName.toLowerCase())) {
								return true;
							}
						}
					}

					if (rightOp instanceof ParameterRef) {
						Type paramType = rightOp.getType();
						Type outerType = outerCaller.getType();
						if (paramType.toString().equals(outerType.toString())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	
	
	
	static class LoopConditionAnalysis {

		boolean isShutDownLoopCondition = false;
		boolean leadsToExitOrThrow = false; 

		public LoopConditionAnalysis(KeyPoint isShutDown) {
			analyze(isShutDown);
		}

		private void analyze(KeyPoint isShutDownPoint) {
			SootMethod method = isShutDownPoint.getMethod();
			Unit isShutDownUnit = isShutDownPoint.getStmt();

			if (!method.hasActiveBody()) return;
			BriefUnitGraph graph = new BriefUnitGraph(method.getActiveBody());
			SimpleLocalDefs localDefs = new SimpleLocalDefs(graph);

			for (Unit unit : graph) {
				if (unit instanceof IfStmt) {
					IfStmt ifStmt = (IfStmt) unit;
					Value condition = ifStmt.getCondition();

					for (ValueBox box : condition.getUseBoxes()) {
						Value value = box.getValue();
						if (value instanceof Local) {
							Local conditionLocal = (Local) value;
							List<Unit> defs = localDefs.getDefsOfAt(conditionLocal, ifStmt);

							if (defs.contains(isShutDownUnit)) {
								
								if (isReachable(graph, ifStmt, isShutDownUnit)) {
									isShutDownLoopCondition = true;

									
									if (checkExitPaths(graph, ifStmt)) {
										leadsToExitOrThrow = true;
									}
									return;
								}
							}
						}
					}
				}
			}
		}

		
		private boolean checkExitPaths(BriefUnitGraph graph, IfStmt ifStmt) {
			
			if (leadsToExit(graph, ifStmt.getTarget())) return true;

			
			
			
			Unit succ = null;
			for(Unit u : graph.getSuccsOf(ifStmt)) {
				if (u != ifStmt.getTarget()) {
					succ = u;
					break;
				}
			}
			if (succ != null && leadsToExit(graph, succ)) return true;

			return false;
		}

		
		private boolean leadsToExit(BriefUnitGraph graph, Unit startUnit) {
			List<Unit> queue = new java.util.LinkedList<>();
			Set<Unit> visited = new HashSet<>();
			queue.add(startUnit);

			int maxDepth = 20; 
			int depth = 0;

			while (!queue.isEmpty() && depth < maxDepth) {
				int size = queue.size();
				for(int i=0; i<size; i++) {
					Unit current = queue.remove(0);
					if (visited.contains(current)) continue;
					visited.add(current);

					
					if (current instanceof ThrowStmt ||
							current instanceof ReturnStmt ||
							current instanceof ReturnVoidStmt) {
						return true;
					}

					queue.addAll(graph.getSuccsOf(current));
				}
				depth++;
			}
			return false;
		}

		private boolean isReachable(BriefUnitGraph graph, Unit startNode, Unit targetNode) {
			List<Unit> queue = new java.util.LinkedList<>();
			Set<Unit> visited = new java.util.HashSet<>();
			queue.addAll(graph.getSuccsOf(startNode));

			while (!queue.isEmpty()) {
				Unit current = queue.remove(0);
				if (visited.contains(current)) continue;
				visited.add(current);

				if (current.equals(targetNode)) {
					return true;
				}
				queue.addAll(graph.getSuccsOf(current));
			}
			return false;
		}
	}
}