package com.example.polystirolbagdes.core;

import java.time.Instant;
import java.util.UUID;

import com.google.gson.annotations.SerializedName;

public class Badge {
	@SerializedName("id")
	private UUID id;
	@SerializedName("name")
	private String name;
	@SerializedName("description")
	private String description;
	@SerializedName("image_url")
	private String imageUrl;
	@SerializedName("badge_type")
	private BadgeType badgeType;
	@SerializedName("unicode_char")
	private String unicodeChar;
	@SerializedName("created_at")
	private Instant createdAt;

	public Badge() {
		// Конструктор по умолчанию для Gson
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public BadgeType getBadgeType() {
		return badgeType;
	}

	public String getUnicodeChar() {
		return unicodeChar;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/**
	 * Конвертирует hex код Unicode символа (например "E000") в строку
	 * @return Unicode символ как строка
	 */
	public String getUnicodeString() {
		if (unicodeChar == null || unicodeChar.isEmpty()) {
			return "";
		}
		try {
			int codePoint = Integer.parseInt(unicodeChar, 16);
			return new String(Character.toChars(codePoint));
		} catch (NumberFormatException e) {
			return "";
		}
	}

	public enum BadgeType {
		TEMPORARY,
		EVENT,
		PERMANENT
	}
}

