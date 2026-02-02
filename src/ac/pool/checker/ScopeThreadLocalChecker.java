package ac.pool.checker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ac.pool.point.InitPoint;
import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;



public class ScopeThreadLocalChecker {

    
    
    private static final List<String> WHITELIST_PREFIXES = Arrays.asList(
            "org.eclipse.jetty.",        
            "org.springframework.",      
            "org.apache.tomcat.",        
            "io.netty.",                 
            "java.util.logging."         
    );

    public static boolean hasThreadLocalMisuse(InitPoint point) {
        
        SootClass hostClass = point.getMethod().getDeclaringClass();

        
        if (isWhitelisted(hostClass)) {
            return false;
        }

        
        List<SootClass> classesToScan = getRelatedClasses(hostClass);

        
        for (SootClass sc : classesToScan) {
            
            if (sc.isLibraryClass() || sc.isJavaLibraryClass()) continue;

            
            if (isWhitelisted(sc)) continue;

            for (SootMethod method : new ArrayList<>(sc.getMethods())) {
                
                if (method.hasActiveBody()) {
                    
                    
                    if (method.isConstructor() || method.isStaticInitializer()) {
                        continue;
                    }

                    if (hasSetButNoRemove(method)) {
                        
                        return true;
                    }
                }
            }
        }
        return false;
    }

    
    private static boolean isWhitelisted(SootClass sc) {
        String name = sc.getName();
        for (String prefix : WHITELIST_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
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

    
    private static boolean hasSetButNoRemove(SootMethod method) {
        boolean hasSet = false;
        boolean hasRemove = false;

        
        if (!method.hasActiveBody()) {
            try {
                method.retrieveActiveBody();
            } catch (Exception e) {
                return false;
            }
        }

        Body body = method.getActiveBody();
        for (Unit unit : body.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    SootMethod invokedMethod = stmt.getInvokeExpr().getMethod();
                    String methodSig = invokedMethod.getSignature();

                    
                    if (methodSig.contains("java.lang.ThreadLocal: void set(java.lang.Object)")) {
                        hasSet = true;

                        
                        
                        if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
                            Value arg = stmt.getInvokeExpr().getArg(0);
                            if (arg instanceof NullConstant) {
                                hasRemove = true; 
                            }
                        }
                    }

                    
                    if (methodSig.contains("java.lang.ThreadLocal: void remove()")) {
                        hasRemove = true;
                    }
                }
            }
        }

        
        return hasSet && !hasRemove;
    }
}