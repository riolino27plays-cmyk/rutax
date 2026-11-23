package com.rutaauto;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RutaAutoClient implements ClientModInitializer {
	public static final String MOD_ID = "rutaauto";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Route route;

	private static boolean enabled = false;
	private static boolean recording = false;
	private static boolean playing = false;

	private static int frameIndex = 0;
	private static int countdown = 0;
	private static float playStartYaw = 0f;

	private static KeyBinding toggleKey;
	private static KeyBinding recordKey;

	@Override
	public void onInitializeClient() {
		route = Route.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
		toggleKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.rutaauto.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
		recordKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.rutaauto.record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

		ClientTickEvents.START_CLIENT_TICK.register(RutaAutoClient::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(RutaAutoClient::onEndTick);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			enabled = false;
			recording = false;
			stopPlayback(client);
		});

		registerCommands();
	}

	// ------------------------------------------------------------------ ticks

	/** Antes de que el jugador procese su input: aquí forzamos las teclas de la ruta. */
	private static void onStartTick(MinecraftClient client) {
		if (!playing) return;

		if (client.player == null || client.world == null) {
			stopPlayback(client);
			return;
		}
		if (recording) {
			stopPlayback(client);
			return;
		}
		// Pausa mientras haya una pantalla abierta (inventario, chat, menú...).
		if (client.currentScreen != null) {
			releaseAll(client);
			return;
		}
		// Ya se aplicaron todos los frames: soltar teclas y esperar al siguiente ciclo.
		if (frameIndex >= route.frames.size()) {
			stopPlayback(client);
			countdown = secondsToTicks(route.intervalSeconds);
			return;
		}

		Route.Frame f = route.frames.get(frameIndex++);
		applyKeys(client, f.keys);
		if (route.applyCamera) {
			client.player.setYaw(playStartYaw + (f.yaw - route.startYaw));
			client.player.setPitch(f.pitch);
		}
	}

	private static void onEndTick(MinecraftClient client) {
		while (toggleKey.wasPressed()) {
			toggleEnabled(client);
		}
		while (recordKey.wasPressed()) {
			toggleRecording(client);
		}

		if (client.player == null || client.world == null) return;

		if (recording) {
			if (route.frames.size() >= Route.MAX_FRAMES) {
				toggleRecording(client); // límite alcanzado: parar y guardar
				return;
			}
			GameOptions o = client.options;
			int keys = 0;
			if (o.forwardKey.isPressed()) keys |= Route.FORWARD;
			if (o.backKey.isPressed()) keys |= Route.BACK;
			if (o.leftKey.isPressed()) keys |= Route.LEFT;
			if (o.rightKey.isPressed()) keys |= Route.RIGHT;
			if (o.jumpKey.isPressed()) keys |= Route.JUMP;
			if (o.sneakKey.isPressed()) keys |= Route.SNEAK;
			if (o.sprintKey.isPressed()) keys |= Route.SPRINT;
			route.frames.add(new Route.Frame(keys, client.player.getYaw(), client.player.getPitch()));
			return;
		}

		if (enabled && !playing && !route.frames.isEmpty() && client.currentScreen == null) {
			if (--countdown <= 0) {
				startPlayback(client);
			}
		}
	}

	// ---------------------------------------------------------------- acciones

	private static void toggleEnabled(MinecraftClient client) {
		if (recording) {
			say(client, "Para la grabación antes de activar el mod (tecla J).");
			return;
		}
		enabled = !enabled;
		if (enabled) {
			if (route.frames.isEmpty()) {
				enabled = false;
				say(client, "No hay ruta grabada. Pulsa J para grabar una.");
				return;
			}
			countdown = 20; // primera ejecución en 1 segundo
			say(client, "Ruta automática ACTIVADA (cada " + route.intervalSeconds + " s)");
		} else {
			stopPlayback(client);
			say(client, "Ruta automática DESACTIVADA");
		}
	}

	private static void toggleRecording(MinecraftClient client) {
		if (client.player == null) return;
		if (!recording) {
			enabled = false;
			stopPlayback(client);
			route.frames.clear();
			route.startYaw = client.player.getYaw();
			recording = true;
			say(client, "Grabando ruta... (J para parar)");
		} else {
			recording = false;
			route.save();
			say(client, "Ruta guardada: " + route.frames.size() + " ticks (" + String.format("%.1f", route.frames.size() / 20.0) + " s)");
		}
	}

	private static void startPlayback(MinecraftClient client) {
		if (client.player == null || route.frames.isEmpty()) return;
		playing = true;
		frameIndex = 0;
		playStartYaw = client.player.getYaw();
	}

	private static void stopPlayback(MinecraftClient client) {
		playing = false;
		frameIndex = 0;
		releaseAll(client);
	}

	// ------------------------------------------------------------------- teclas

	private static void applyKeys(MinecraftClient client, int keys) {
		GameOptions o = client.options;
		force(o.forwardKey, (keys & Route.FORWARD) != 0, false);
		force(o.backKey, (keys & Route.BACK) != 0, false);
		force(o.leftKey, (keys & Route.LEFT) != 0, false);
		force(o.rightKey, (keys & Route.RIGHT) != 0, false);
		force(o.jumpKey, (keys & Route.JUMP) != 0, false);
		force(o.sneakKey, (keys & Route.SNEAK) != 0, o.getSneakToggled().getValue());
		force(o.sprintKey, (keys & Route.SPRINT) != 0, o.getSprintToggled().getValue());
	}

	private static void releaseAll(MinecraftClient client) {
		if (client == null || client.options == null) return;
		applyKeys(client, 0);
	}

	/**
	 * Fuerza el estado de una tecla. Si la tecla está en modo "alternar" (toggle),
	 * setPressed(true) cambia el estado en vez de fijarlo, así que solo se llama
	 * cuando el estado actual difiere del deseado.
	 */
	private static void force(KeyBinding key, boolean wanted, boolean sticky) {
		if (key.isPressed() == wanted) return;
		key.setPressed(sticky ? true : wanted);
	}

	// ----------------------------------------------------------------- comandos

	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("rutaauto")
						.then(ClientCommandManager.literal("toggle").executes(ctx -> {
							toggleEnabled(ctx.getSource().getClient());
							return 1;
						}))
						.then(ClientCommandManager.literal("record").executes(ctx -> {
							toggleRecording(ctx.getSource().getClient());
							return 1;
						}))
						.then(ClientCommandManager.literal("clear").executes(ctx -> {
							MinecraftClient c = ctx.getSource().getClient();
							enabled = false;
							recording = false;
							stopPlayback(c);
							route.frames.clear();
							route.save();
							say(c, "Ruta borrada.");
							return 1;
						}))
						.then(ClientCommandManager.literal("interval")
								.then(ClientCommandManager.argument("segundos", DoubleArgumentType.doubleArg(1.0, 86400.0))
										.executes(ctx -> {
											route.intervalSeconds = DoubleArgumentType.getDouble(ctx, "segundos");
											route.save();
											say(ctx.getSource().getClient(), "Intervalo: " + route.intervalSeconds + " s");
											return 1;
										})))
						.then(ClientCommandManager.literal("camera")
								.then(ClientCommandManager.argument("activar", BoolArgumentType.bool())
										.executes(ctx -> {
											route.applyCamera = BoolArgumentType.getBool(ctx, "activar");
											route.save();
											say(ctx.getSource().getClient(), "Reproducir cámara: " + route.applyCamera);
											return 1;
										})))
						.then(ClientCommandManager.literal("status").executes(ctx -> {
							say(ctx.getSource().getClient(),
									"activo=" + enabled + ", grabando=" + recording
											+ ", ticks=" + route.frames.size()
											+ ", intervalo=" + route.intervalSeconds + " s"
											+ ", cámara=" + route.applyCamera);
							return 1;
						}))));
	}

	private static void say(MinecraftClient client, String msg) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal("[RutaAuto] " + msg), true);
		}
	}

	private static int secondsToTicks(double seconds) {
		return (int) Math.max(20, Math.round(seconds * 20.0));
	}
}
