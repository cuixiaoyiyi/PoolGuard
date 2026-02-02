package ac.pool;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import ac.component.PointCollectorAsyncTask;
import ac.component.PointCollectorExecutor;
import ac.component.PointCollectorThread;
import ac.constant.ExecutorSig;
import ac.constant.ThreadSig;
import ac.pool.checker.HTRChecker;
import ac.pool.checker.INRChecker;
import ac.pool.checker.PoolCheck;
import ac.pool.point.InitPoint;
import ac.pool.point.KeyPoint;
import ac.pool.point.PointCollector;
import ac.util.AsyncInherit;
import ac.util.Log;
import soot.G;
import soot.Pack;
import soot.PackManager;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.Body;
import soot.BodyTransformer;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowConfiguration.CodeEliminationMode;
import soot.jimple.infoflow.InfoflowConfiguration.SootIntegrationMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration.CallbackAnalyzer;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;








public class PoolMain {

	public static long startTime = 0; 

	public static long preprocessStartTime = 0; 

	
	public static String Android_Platforms = "/Users/***********/Library/Android/sdk/platforms";

	
	static String inputPath = "/Users/***********/Downloads/F-Droid.apk";
	
	public static final String Output = "./Felidae-ThreadPool-output/";



	
	private static boolean isApk = false;
	
	private static List<String> jars = new ArrayList<String>();
	
	private static long maxUsedMemory = 0;
	
	public static int misuseNum = 0;

	
	static class MemoryThread extends Thread {
		@Override
		public void run() {
			while (true && !isInterrupted()) {
				try {

					long maxUsedMemory_t = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
					maxUsedMemory = Math.max(maxUsedMemory, maxUsedMemory_t);
					sleep(2 * 100);
				} catch (InterruptedException e) {
					long maxUsedMemory_t = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
					maxUsedMemory = Math.max(maxUsedMemory, maxUsedMemory_t);
					break;
				}
			}
		}
	}

	public static void main(String[] args) {




		args = new String[]{"/Users/***********/Downloads/Felidae-ThreadPool-main/TestProject/jetty-server-12.1.4.jar"};

		Thread thread = new MemoryThread();
		thread.setDaemon(true);  
		thread.start();
		startTime = System.currentTimeMillis();
		inputPath = args[0];

		if (args.length > 1) {
			Android_Platforms = args[1];
		}

		if (inputPath.toLowerCase().endsWith(".apk")) {
			isApk = true;
			startApk();
		}
		else if (inputPath.toLowerCase().endsWith(".jar")) {
			jars.add(inputPath);
			startJars();
		}
		else 
		{

			jars.addAll(getJars(inputPath));
			if(jars.isEmpty()) {
				jars.add(inputPath);
			}
			startJars();
		}










		try{
			thread.interrupt();
		}catch(Exception e){
			throw new RuntimeException();
		}

		ThreadErrorRecord.recordTime("", "", preprocessStartTime, startTime);
		Log.i("## end ", inputPath);

	}

	
	private static void startJars() {

		G.reset();
		Options.v().set_src_prec(Options.src_prec_c);
		Options.v().set_process_dir(jars);
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_no_writeout_body_releasing(true); 
		
		Options.v().set_output_format(Options.output_format_none);
		Options.v().allow_phantom_refs();
		Options.v().set_whole_program(true);
		Options.v().set_exclude(getExcludeList());

		
		Options.v().setPhaseOption("cg.spark", "on");
		Options.v().setPhaseOption("cg.spark", "string-constants:true");

		soot.Main.v().autoSetOptions();

		
		try {
			Scene.v().loadNecessaryClasses();
			
			
		} catch (Throwable e) {
			e.printStackTrace();
		}

		
		
		List<String> methodsToCall = new ArrayList<>();
		for (SootClass sc : Scene.v().getApplicationClasses()) {
			
			if (sc.isInterface() || sc.isPhantom() || sc.isAbstract()) {
				continue;
			}

			for (SootMethod sm : sc.getMethods()) {
				
				if (sm.isConcrete() && sm.isPublic() && !sm.isConstructor()) {
					methodsToCall.add(sm.getSignature());
				}
			}
		}

		
		
		SootMethod dummyMain = null;
		if (!methodsToCall.isEmpty()) {
			
			DefaultEntryPointCreator creator = new DefaultEntryPointCreator(methodsToCall);
			dummyMain = creator.createDummyMain();
			Scene.v().addBasicClass(dummyMain.getDeclaringClass().getName());
			Scene.v().setEntryPoints(Collections.singletonList(dummyMain));
			System.out.println("虚拟入口已构建，包含调用目标: " + methodsToCall.size() + " 个");
		} else {
			System.err.println("错误：未找到任何可调用的 Public 方法，无法构建入口。");
			return;
		}



		Infoflow infoflow = new Infoflow(inputPath, false);

		infoflow.getConfig().setSootIntegrationMode(SootIntegrationMode.UseExistingInstance);

		infoflow.getConfig().setCallgraphAlgorithm(CallgraphAlgorithm.SPARK);

		infoflow.getConfig().setTaintAnalysisEnabled(false);

		infoflow.getConfig().setCodeEliminationMode(CodeEliminationMode.NoCodeElimination);
		try {

			Method constructCG = Infoflow.class.getDeclaredMethod("runAnalysis", ISourceSinkManager.class);

			constructCG.setAccessible(true);

			constructCG.invoke(infoflow, (ISourceSinkManager) null);

			detectMisuse();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void startApk() {
		Log.i("## start apk ", inputPath);



		SetupApplication application = new SetupApplication(Android_Platforms, inputPath);

		application.getConfig().setMergeDexFiles(true);


		application.getConfig().getCallbackConfig().setCallbackAnalyzer(CallbackAnalyzer.Fast);

		application.getConfig().getCallbackConfig().setCallbackAnalysisTimeout(5 * 60);

		application.constructCallgraph();

		detectMisuse();
	}

	private static void detectMisuse() {
		Log.i("----------detector starts ");

		final Set<String> runnableClasses = new HashSet<String>();

		Set<String> iNRClasses = new HashSet<>();

		Set<SootClass> sootClasses = new HashSet<>(Scene.v().getApplicationClasses());
		for (SootClass currentClass : sootClasses) {

			if (AsyncInherit.isInheritedFromRunnable(currentClass)
					|| AsyncInherit.isInheritedFromCallable(currentClass))
			{

				runnableClasses.add(currentClass.getName());

				if (!INRChecker.hasInterruptCheck(currentClass)) {

					iNRClasses.add(currentClass.getName());
				}
			}
		}

		Log.i("iNRClasses.size() = ", iNRClasses.size());

		HTRChecker.init();





		PointCollector executorCollector = new PointCollectorExecutor();
		executorCollector.start(sootClasses);

		
		filterNoisePoints(executorCollector);



		PointCollector threadCollector = new PointCollectorThread();
		threadCollector.start(sootClasses);

		
		filterNoisePoints(threadCollector);




















		


		complementCGFromStartToRun(executorCollector);
		complementCGFromStartToRun(threadCollector);

		
		new PoolCheck(executorCollector, "ExecuteService-", iNRClasses).check();
		new PoolCheck(threadCollector, "Thread-", iNRClasses).check();



		long timeStart = System.currentTimeMillis();
		ExecutorService executor = PoolCheck.executor;
		executor.shutdown();








		try {
			
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				System.out.println("NTT检查未在10秒内完成，强制关闭线程池...");
				executor.shutdownNow(); 

				
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					System.err.println("线程池无法正常关闭");
				}
			}
			System.out.println("所有检查完成，程序退出");
		} catch (InterruptedException e) {
			System.err.println("等待被中断，强制关闭线程池");
			executor.shutdownNow();
			Thread.currentThread().interrupt(); 
		}
	}

	
	private static void filterNoisePoints(PointCollector pointCollector) {
		
		
		
		
		java.util.Iterator<? extends KeyPoint> iterator = pointCollector.getInitialPoints().iterator();

		while (iterator.hasNext()) {
			KeyPoint point = iterator.next();
			SootMethod method = point.getMethod(); 
			String methodName = method.getName();
			String methodSig = method.getSignature();

			
			if (methodName.contains("dummyMainMethod") || methodSig.contains("dummyMainMethod")) {
				iterator.remove();
				continue;
			}

			
			
			
			if (methodName.equals("<init>")) {
				iterator.remove();
				continue;
			}
		}
	}

	static void debug(PointCollector pointCollector) {

		for (SootMethod sootMethod:Scene.v().getSootClass("cn**ios.PoolTest").getMethods()) {

			Log.i(sootMethod.getActiveBody());
		}








		System.exit(0);
	}

	private static List<String> getExcludeList() {
		ArrayList<String> excludeList = new ArrayList<String>();
		excludeList.add("android.*");
		excludeList.add("androidx.*");



		excludeList.add("java.*");
		excludeList.add("sun.*");
		excludeList.add("javax.*");
		excludeList.add("com.sun.*");


		excludeList.add("org.xml.*");
		excludeList.add("org.w3c.*");


		return excludeList;
	}

	
	static void complementCGFromStartToRun(PointCollector pointCollector) {
		for (KeyPoint startPoint : pointCollector.getStartPoints()) {
			for (InitPoint point : pointCollector.getInitialPoints()) {
				if (point.isAliasCaller(startPoint)) { 
					for (RefType refType : point.getCallerPossibleType()) {
						addEdgeFromStartToRunMethod(startPoint, refType.getSootClass());
					}
				}
			}
		}
	}

	
	static void addEdgeFromStartToRunMethod(KeyPoint startPoint, SootClass sootClass) {
		try {
			SootMethod runMethod = sootClass.getMethodUnsafe(ThreadSig.METHOD_SUBSIG_RUN);
			if(runMethod == null) {
				runMethod = sootClass.getMethodByNameUnsafe(ExecutorSig.METHOD_NAME_CALL);
			}
			if(runMethod == null) {
				return;
			}
			Edge edge = new Edge(startPoint.getMethod(), startPoint.getStmt(), runMethod);
			Scene.v().getCallGraph().addEdge(edge);
		} catch (Exception e) {

		}

	}

	
	public static Set<String> getJars(String dic) {
		File file = new File(dic);
		Set<String> jarList = new HashSet<String>();
		if (file.isDirectory()) {
			File[] fileList = file.listFiles();
			if (fileList != null) {
				for (File subFile : fileList) {
					String string = subFile.getAbsolutePath();
					if (subFile.isDirectory()) {
						jarList.addAll(getJars(string));
					} else {
						if (string.toLowerCase().endsWith(".jar")) {
							jarList.add(string);
						}
					}

				}
			}
		}
		return jarList;
	}

	
	public static String getOutputPath(String sub) {
		String path = null;
		if (isApk || inputPath.toLowerCase().endsWith(".jar")) {
			path = inputPath.substring(0, inputPath.lastIndexOf("."));
		} else {
			path = inputPath;
		}
		while (path.endsWith(File.separator)) {
			path = path.substring(0, path.length() - 1);
		}
		int start = path.lastIndexOf(File.separator);
		if (start == -1) {
			start = 0;
		}
		path = Output + path.substring(start) + File.separator + sub + File.separator;
		File file = new File(path);
		if (!file.exists()) {
			file.mkdirs();
		}
		return path;
	}

	public static String getApkFullPath() {
		return inputPath;
	}

	public static boolean isApk() {
		return isApk;
	}
}
