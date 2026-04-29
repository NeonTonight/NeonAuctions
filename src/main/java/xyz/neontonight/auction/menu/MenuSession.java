package xyz.neontonight.auction.menu;

import java.util.List;

public record MenuSession(MenuType type, int page, List<String> ids) {
}
