package com.example.polystirolutility.core;

import com.google.gson.annotations.SerializedName;

public class ResourceGoal {
	@SerializedName("resource_type")
	private String resourceType;

	@SerializedName("name")
	private String name;

	@SerializedName("current_amount")
	private int currentAmount;

	@SerializedName("target_amount")
	private int targetAmount;

	@SerializedName("goal_id")
	private String goalId;

	@SerializedName("is_active")
	private boolean isActive;

	public String getResourceType() {
		return resourceType;
	}

	public String getName() {
		return name;
	}

	public int getCurrentAmount() {
		return currentAmount;
	}

	public int getTargetAmount() {
		return targetAmount;
	}

	public String getGoalId() {
		return goalId;
	}

	public boolean isActive() {
		return isActive;
	}
}
