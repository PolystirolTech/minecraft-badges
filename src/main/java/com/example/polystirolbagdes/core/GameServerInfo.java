package com.example.polystirolbagdes.core;

import java.util.UUID;

public class GameServerInfo {
	@SerializedName("id")
	private UUID id;
	@SerializedName("name")
	private String name;
	@SerializedName("resource_pack_url")
	private String resourcePackUrl;
	@SerializedName("resource_pack_hash")
	private String resourcePackHash;

	public GameServerInfo() {
		// Конструктор по умолчанию для Gson
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getResourcePackUrl() {
		return resourcePackUrl;
	}

	public String getResourcePackHash() {
		return resourcePackHash;
	}
}

