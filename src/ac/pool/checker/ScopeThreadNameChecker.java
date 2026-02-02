package ac.pool.checker;

import java.util.ArrayList;
import java.util.List;
import ac.pool.point.InitPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;


public class ScopeThreadNameChecker {

    
    private static final String THREAD_SET_NAME_SIG = "<java.lang.Thread: void setName(java.lang.String)>";
    
    private static final String THREAD_FACTORY_NEW_THREAD_SUBSIG = "java.lang.Thread newThread(java.lang.Runnable)";

    public static boolean hasMisuse(InitPoint initPoint) {
        SootClass hostClass = initPoint.getMethod().getDeclaringClass();
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        
        if (isNamingThreadFactoryDefined(classesToScan)) {
            return false; 
        }

        
        if (isNamingDoneInTask(classesToScan)) {
            return false; 
        }

        
        return true;
    }

    
    private static boolean isNamingThreadFactoryDefined(List<SootClass> classesToScan) {
        SootClass threadFactoryClass = Scene.v().getSootClassUnsafe("java.util.concurrent.ThreadFactory");
        if (threadFactoryClass == null) return false;

        for (SootClass sc : classesToScan) {
            
            if (sc.implementsInterface(String.valueOf(threadFactoryClass))) {
                SootMethod newThreadMethod = sc.getMethodUnsafe(THREAD_FACTORY_NEW_THREAD_SUBSIG);

                if (newThreadMethod != null && newThreadMethod.hasActiveBody()) {
                    
                    if (methodContainsSetName(newThreadMethod)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    
    private static boolean isNamingDoneInTask(List<SootClass> classesToScan) {
        for (SootClass sc : classesToScan) {
            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                String name = method.getName();
                
                if ((name.equals("run") || name.equals("call") || name.startsWith("lambda$"))
                        && method.hasActiveBody()) {

                    if (methodContainsSetName(method)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    
    private static boolean methodContainsSetName(SootMethod method) {
        if (!method.hasActiveBody()) return false;
        Body body = method.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    if (stmt.getInvokeExpr().getMethod().getSignature().equals(THREAD_SET_NAME_SIG)) {
                        return true;
                    }
                }
            }
        }
        return false;
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