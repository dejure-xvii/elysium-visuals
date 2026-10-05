package dev.elysium.visuals.client.region;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * A message on WorldEdit's CUI channel: plain UTF-8 text such as
 * {@code "p|0|10|64|-5|1"} (both directions use the same format).
 */
public record CuiPayload(String message) implements CustomPacketPayload {
	public static final Type<CuiPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldedit", "cui"));
	public static final StreamCodec<ByteBuf, CuiPayload> CODEC = StreamCodec.of(
			(buf, payload) -> buf.writeBytes(payload.message().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				byte[] bytes = new byte[buf.readableBytes()];
				buf.readBytes(bytes);
				return new CuiPayload(new String(bytes, StandardCharsets.UTF_8));
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
