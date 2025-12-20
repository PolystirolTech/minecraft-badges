package com.example.polystirolutility.core;

import com.google.gson.annotations.SerializedName;

public class ResourceCollectionResponse {
	@SerializedName("success")
	private boolean success;

	@SerializedName("message")
	private String message;

	@SerializedName("current_amount")
	private int currentAmount;

	public ResourceCollectionResponse() {
		// Конструктор по умолчанию для Gson
	}

	public boolean isSuccess() {
		return success;
	}

	public String getMessage() {
		return message;
	}

	public int getCurrentAmount() {
		return currentAmount;
	}
}

