package com.sandwich.koan.ui;

import com.sandwich.koan.result.KoanSuiteResult;

public interface SuitePresenter {

	public void displayResult(KoanSuiteResult result);
	public void displayError(String error);
	public void displayMessage(String error);
	public void clearMessages();

}
