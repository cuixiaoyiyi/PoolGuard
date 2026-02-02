package ac.pool.checker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.OneParaKeyPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.DivExpr;
import soot.jimple.RemExpr;
import soot.jimple.ThrowStmt;


public class ScopeExceptionHandlerChecker {

    public static boolean hasMisuse(InitPoint initPoint,
                                    Set<OneParaKeyPoint> setFactoryPoints,
                                    Set<KeyPoint> setUncaughtExceptionHandlerPoints) {

        
        
        

        SootClass hostClass = initPoint.getMethod().getDeclaringClass();
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        for (SootClass sc : classesToScan) {
            if (sc.isLibraryClass() || sc.isJavaLibraryClass()) continue;

            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                
                if (method.hasActiveBody() && isTaskMethod(method)) {
                    
                    if (hasUnhandledRiskyInstruction(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    
    private static boolean hasUnhandledRiskyInstruction(SootMethod method) {
        Body body = method.getActiveBody();

        for (Unit unit : body.getUnits()) {
            
            if (isRiskyUnit(unit)) {
                
                if (!isProtectedByTrap(body, unit)) {
                    
                    return true;
                }
            }
        }
        return false;
    }

    
    private static boolean isRiskyUnit(Unit unit) {
        
        if (unit instanceof AssignStmt) {
            Value rightOp = ((AssignStmt) unit).getRightOp();
            
            
            if (rightOp instanceof DivExpr || rightOp instanceof RemExpr) {
                return true;
            }
        }






        
        
        

        return false;
    }

    
    private static boolean isProtectedByTrap(Body body, Unit unit) {
        for (Trap trap : body.getTraps()) {
            
            
            Unit current = trap.getBeginUnit();
            while (current != null && current != trap.getEndUnit()) {
                if (current == unit) {
                    return true; 
                }
                current = body.getUnits().getSuccOf(current);
            }
        }
        return false;
    }

    private static boolean isTaskMethod(SootMethod method) {
        String name = method.getName();
        
        return name.equals("run") || name.equals("call") || name.startsWith("lambda$");
    }

    private static List<SootClass> getRelatedClasses(SootClass hostClass) {
        List<SootClass> list = new ArrayList<>();
        list.add(hostClass);
        String hostName = hostClass.getName();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (sc.getName().startsWith(hostName + "$")) {
                list.add(sc);
            }
        }
        return list;
    }
}