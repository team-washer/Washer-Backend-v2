package team.washer.server.v2.global.thirdparty.discord.data;

import lombok.Getter;

@Getter
public enum EmbedColor {
    ERROR(0xE74C3C), WARNING(0xF39C12), INFO(0x85C1E9), SUCCESS(0x27AE60);

    private final int color;

    EmbedColor(int color) {
        this.color = color;
    }
}
