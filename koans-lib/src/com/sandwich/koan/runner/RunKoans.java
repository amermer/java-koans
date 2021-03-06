package com.sandwich.koan.runner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.sandwich.koan.Koan;
import com.sandwich.koan.KoanMethod;
import com.sandwich.koan.cmdline.behavior.AbstractArgumentBehavior;
import com.sandwich.koan.constant.KoanConstants;
import com.sandwich.koan.path.PathToEnlightenment;
import com.sandwich.koan.path.PathToEnlightenment.Path;
import com.sandwich.koan.path.xmltransformation.KoanElementAttributes;
import com.sandwich.koan.result.KoanMethodResult;
import com.sandwich.koan.result.KoanSuiteResult;
import com.sandwich.koan.result.KoanSuiteResult.KoanResultBuilder;
import com.sandwich.koan.util.ApplicationUtils;
import com.sandwich.util.ExceptionUtils;
import com.sandwich.util.KoanComparator;
import com.sandwich.util.io.CompilationListener;
import com.sandwich.util.io.DynamicClassLoader;
import com.sandwich.util.io.FileUtils;
import com.sandwich.util.io.KoanSuiteCompilationListener;

public class RunKoans extends AbstractArgumentBehavior {

	private Path pathToEnlightenment;
	
	public RunKoans(){
		this(null); // use defaults coded in getters
	}
	
	public RunKoans(Path pathToEnlightenment){
		this.pathToEnlightenment = pathToEnlightenment;
	}
	
	public void run(String value) {
		ApplicationUtils.getPresenter().clearMessages();
		ApplicationUtils.getPresenter().displayResult(runKoans());
	}

	KoanSuiteResult runKoans() {
		List<String> passingSuites = new ArrayList<String>();
		List<String> failingSuites = new ArrayList<String>();
		String level = null;
		KoanMethodResult failure = null;
		DynamicClassLoader loader = new DynamicClassLoader();
		Path pathToEnlightement = getPathToEnlightement();
		KoanSuiteCompilationListener compilationListener = new KoanSuiteCompilationListener();
		int successfull = 0;
		for (Entry<String, Map<String, Map<String, KoanElementAttributes>>> packages : pathToEnlightement) {
			for (Entry<String, Map<String, KoanElementAttributes>> e : packages.getValue().entrySet()) {
				String name = e.getKey().substring(e.getKey().lastIndexOf('.')+1);
				if(failure == null){
					Object suite = safelyConstructSuite(loader, compilationListener, e);
					final List<KoanElementAttributes> attributes = new ArrayList<KoanElementAttributes>(e.getValue().values());
					final List<KoanMethod> methods = mergeJavaFilesMethodsAndThoseInXml(suite, attributes, pathToEnlightement.getOnlyMethodNameToRun());
					Collections.sort(methods, new KoanComparator());
					for (final KoanMethod koan : methods) {
						KoanMethodResult result = KoanMethodRunner.run(suite, koan);
						if(KoanMethodResult.PASSED != result){
							// Test failed!
							failure = result;
							failingSuites.add(name);
							break;
						}else{
							successfull++;
						}
					}
					if (failure == null) {
						passingSuites.add(name);
					}
					level = packages.getKey();
				}else{
					failingSuites.add(name);
				}
			}
		}
		return new KoanResultBuilder()	.level(level)
										.numberPassing(successfull)
										.totalNumberOfKoanMethods(pathToEnlightement.getTotalNumberOfKoans())
										.methodResult(failure)
										.passingCases(passingSuites).remainingCases(failingSuites).build();
	}

	private Object safelyConstructSuite(DynamicClassLoader loader,
			KoanSuiteCompilationListener compilationListener,
			Entry<String, Map<String, KoanElementAttributes>> e) {
		// this is written strangely so stack trace will always be the same when constructSuite fails
		// this permits the app to only show the compilation failure once, despite the fact this
		// is getting hit every second.
		Object suite = null;
		while(suite == null || compilationListener.isLastCompilationAttemptFailure()) {
			suite = constructSuite(loader, e.getKey(), compilationListener);
			if(compilationListener.isLastCompilationAttemptFailure()){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {}
			}
		}
		return suite;
	}

	private List<KoanMethod> mergeJavaFilesMethodsAndThoseInXml(Object suite,
			final List<KoanElementAttributes> attributes, String onlyMethodNameToRun) {
		final List<KoanMethod> methods = new ArrayList<KoanMethod>();
		final List<String> methodNames = new ArrayList<String>();
		for(KoanElementAttributes attributeSet : attributes){
			if(isMethodEligibleForRunning(onlyMethodNameToRun, attributeSet.name)){
				KoanMethod method = KoanMethod.getInstance(attributeSet);
				methods.add(method);
				methodNames.add(method.getMethod().getName());
			}
		}
		for(Method method : suite.getClass().getMethods()){
			if(!methodNames.contains(method.getName()) &&
					isMethodEligibleForRunning(onlyMethodNameToRun, method.getName()) &&
					method.getAnnotation(Koan.class) != null){
				methods.add(KoanMethod.getInstance(method));
			}
		}
		return methods;
	}

	private boolean isMethodEligibleForRunning(String onlyMethodNameToRun, String name) {
		return onlyMethodNameToRun == null || onlyMethodNameToRun.equals(name);
	}

	private Object constructSuite(DynamicClassLoader loader, String className, CompilationListener listener) {
		Object suite;
		try {
			Class<?> clazz = loader.loadClass(className, listener);
			if(!clazz.isAnonymousClass()){
				suite = clazz.newInstance();
			}else{
				Class<?> enclosingClass = clazz.getEnclosingClass();
				Constructor<?> cons = null;
				for(Constructor<?> c : clazz.getDeclaredConstructors()){
					for(Class<?> p : c.getParameterTypes()){
						if(p == enclosingClass){
							cons = c;
							break;
						}
					}
				}
				cons.setAccessible(true);
				suite = cons.newInstance(enclosingClass.newInstance());
			}
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		} catch (Error e1) {
			if(e1.getClass() == Error.class && e1.getMessage().contains("Unresolved compilation problem")) {
				listener.compilationFailed(FileUtils.getSourceFileFromClass(className), 
						new String[]{"(compilation yielded a class, but it cannot be cereated)"}, 
						-1, "Unable to load class \""+className+"\"." + KoanConstants.EOL + 
						ExceptionUtils.convertToPopulatedStackTraceString(e1), e1);
				return null; // just consume this exception, this will have been logged and is handled
			}else{
				throw new RuntimeException(e1);
			}
		}
		return suite;
	}

	private Path getPathToEnlightement() {
		if(pathToEnlightenment == null){
			pathToEnlightenment = PathToEnlightenment.getPathToEnlightment();
		}
		return pathToEnlightenment;
	}
	
}
