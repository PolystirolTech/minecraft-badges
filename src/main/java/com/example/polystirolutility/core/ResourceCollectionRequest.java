package com.example.polystirolutility.core;

import com.google.gson.annotations.SerializedName;

public class ResourceCollectionRequest {
	@SerializedName("server_uuid")
	private String serverUuid;

	@SerializedName("resource_type")
	private String resourceType;

	@SerializedName("amount")
	private int amount;

	public ResourceCollectionRequest() {
		// Конструктор по умолчанию для Gson
	}

	public ResourceCollectionRequest(String serverUuid, String resourceType, int amount) {
		this.serverUuid = serverUuid;
		this.resourceType = resourceType;
		this.amount = amount;
	}

	public String getServerUuid() {
		return serverUuid;
	}

	public String getResourceType() {
		return resourceType;
	}

	public int getAmount() {
		return amount;
	}
}

