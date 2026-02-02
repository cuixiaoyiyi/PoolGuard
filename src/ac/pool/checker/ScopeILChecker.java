package ac.pool.checker;

import ac.pool.point.KeyPoint;
import soot.*;
import soot.jimple.*;

import soot.jimple.toolkits.annotation.logic.Loop;
import soot.toolkits.graph.LoopNestTree;

import java.util.HashSet;
import java.util.Set;

/**
 * ScopeILChecker (Final Corrected)
 * 修正了 Loop 类的引用错误。
 */
public class ScopeILChecker {

    /**
     * 检测是否存在 IL (Infinite Loop) 误用
     * @return true = 存在误用 (Bug); false = 安全 (Safe)
     */
    public static boolean isShutDownMisuse(Set<KeyPoint> globalShutdowns, KeyPoint isShutDownPoint) {

        
        if (!isInLoop(isShutDownPoint)) {
            return false; 
        }

        
        
        String checkVarName = getTraceableVariableName(isShutDownPoint);
        if (checkVarName == null) return true; 

        SootClass checkClass = isShutDownPoint.getMethod().getDeclaringClass();

        
        for (KeyPoint shutDownPoint : globalShutdowns) {
            SootClass shutdownClass = shutDownPoint.getMethod().getDeclaringClass();

            
            if (!isScopeRelated(checkClass, shutdownClass)) {
                continue;
            }

            
            String closeVarName = getTraceableVariableName(shutDownPoint);

            
            if (isNameFuzzyMatch(checkVarName, closeVarName)) {
                return false; 
            }
        }

        
        return true;
    }

    
    
    

    private static boolean isInLoop(KeyPoint point) {
        SootMethod method = point.getMethod();
        if (!method.hasActiveBody()) return false;

        Body body = method.getActiveBody();
        Stmt targetStmt = point.getStmt();

        
        LoopNestTree loopNestTree = new LoopNestTree(body);

        
        for (Loop loop : loopNestTree) {
            
            if (loop.getLoopStatements().contains(targetStmt)) {
                return true;
            }
        }
        return false;
    }

    
    
    

    private static boolean isScopeRelated(SootClass c1, SootClass c2) {
        if (c1.equals(c2)) return true;
        if (c1.hasOuterClass() && c1.getOuterClass().equals(c2)) return true;
        if (c2.hasOuterClass() && c2.getOuterClass().equals(c1)) return true;
        return false;
    }

    private static boolean isNameFuzzyMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String n1 = simplify(name1);
        String n2 = simplify(name2);
        return n1.contains(n2) || n2.contains(n1);
    }

    private static String simplify(String name) {
        return name.replace("val$", "")
                .replace("access$", "")
                .replace("this.", "")
                .toLowerCase().trim();
    }

    private static String getTraceableVariableName(KeyPoint point) {
        Unit unit = point.getStmt();
        SootMethod method = point.getMethod();
        Value baseValue = null;

        if (unit instanceof Stmt) {
            Stmt stmt = (Stmt) unit;
            if (stmt.containsInvokeExpr()) {
                if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                    baseValue = ((InstanceInvokeExpr) stmt.getInvokeExpr()).getBase();
                }
            }
        }

        if (baseValue == null) return null;

        if (baseValue instanceof Local) {
            return traceOrigin(method, (Local) baseValue, new HashSet<>());
        }
        return baseValue.toString();
    }

    private static String traceOrigin(SootMethod method, Local local, Set<Local> visited) {
        if (!method.hasActiveBody() || visited.contains(local)) return local.toString();
        visited.add(local);

        for (Unit u : method.getActiveBody().getUnits()) {
            if (u instanceof DefinitionStmt) {
                DefinitionStmt def = (DefinitionStmt) u;
                if (def.getLeftOp().equals(local)) {
                    Value right = def.getRightOp();
                    if (right instanceof FieldRef) {
                        return ((FieldRef) right).getField().getName();
                    }
                    if (right instanceof Local) {
                        return traceOrigin(method, (Local) right, visited);
                    }
                }
            }
        }
        return local.toString();
    }
}