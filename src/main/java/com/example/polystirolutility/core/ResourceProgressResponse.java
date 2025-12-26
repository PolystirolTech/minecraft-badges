package com.example.polystirolutility.core;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public class ResourceProgressResponse {
	@SerializedName("server_id")
	private String serverId;

	@SerializedName("server_name")
	private String serverName;

	@SerializedName("resources")
	private List<ResourceGoal> resources;

	public String getServerId() {
		return serverId;
	}

	public String getServerName() {
		return serverName;
	}

	public List<ResourceGoal> getResources() {
		return resources;
	}
}
