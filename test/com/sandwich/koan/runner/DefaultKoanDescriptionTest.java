package com.sandwich.koan.runner;

import java.util.List;
import java.util.Map.Entry;

import org.junit.Test;

import com.sandwich.koan.Koan;
import com.sandwich.koan.KoanConstants;
import com.sandwich.koan.KoanMethod;
import com.sandwich.koan.runner.CommandLineTestCase.AssertionFailedException;

public class DefaultKoanDescriptionTest {

	@Test
	public void defaultKoanDescriptions() throws Exception {
		StringBuilder exceptionStringBuilder = new StringBuilder(KoanConstants.EOL);
		for (Entry<Object, List<KoanMethod>> suiteAndKoans : 
			new KoanSuiteRunner().getPathToEnlightenment().koanMethodsBySuiteByPackage
				.entrySet().iterator().next().getValue().entrySet()) {
			for(KoanMethod koan : suiteAndKoans.getValue()){
				Koan annotation = koan.getMethod().getAnnotation(Koan.class);
				if (annotation != null
						&& KoanConstants.DEFAULT_KOAN_DESC.equals(annotation.value())) {
					exceptionStringBuilder.append(suiteAndKoans.getKey().getClass().getName()).append('.')
							.append(koan.getMethod().getName()).append(KoanConstants.EOL);
				}
			}
		}
		String exceptionString = exceptionStringBuilder.toString();
		if(!exceptionString.trim().isEmpty()){
			throw new AssertionFailedException(
					new StringBuilder(
							KoanConstants.EOL).append(
							"Following still have default Koan description:").append(
							exceptionString).toString());
		}
	}
	
}
