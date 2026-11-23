package com.rutaauto;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Ruta grabada + ajustes. Se guarda en config/rutaauto.json */
public class Route {
	public static final int FORWARD = 1;
	public static final int BACK = 2;
	public static final int LEFT = 4;
	public static final int RIGHT = 8;
	public static final int JUMP = 16;
	public static final int SNEAK = 32;
	public static final int SPRINT = 64;

	/** Máximo de ticks grabables (20 ticks = 1 s) -> 10 minutos. */
	public static final int MAX_FRAMES = 20 * 60 * 10;

	private static final Gson GSON = new Gson();

	public double intervalSeconds = 30.0;
	public boolean applyCamera = true;
	public float startYaw = 0f;
	public List<Frame> frames = new ArrayList<>();

	public static class Frame {
		public int keys;
		public float yaw;
		public float pitch;

		public Frame() {
		}

		public Frame(int keys, float yaw, float pitch) {
			this.keys = keys;
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("rutaauto.json");
	}

	public static Route load() {
		Path p = file();
		if (Files.exists(p)) {
			try {
				Route r = GSON.fromJson(Files.readString(p), Route.class);
				if (r != null) {
					if (r.frames == null) r.frames = new ArrayList<>();
					if (r.intervalSeconds < 1.0) r.intervalSeconds = 1.0;
					return r;
				}
			} catch (Exception e) {
				RutaAutoClient.LOGGER.warn("No se pudo leer {}", p, e);
			}
		}
		return new Route();
	}

	public void save() {
		try {
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException e) {
			RutaAutoClient.LOGGER.warn("No se pudo guardar la ruta", e);
		}
	}
}
