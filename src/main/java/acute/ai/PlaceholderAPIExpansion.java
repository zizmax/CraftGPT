package acute.ai;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    CraftGPT craftGPT;

    public PlaceholderAPIExpansion(CraftGPT craftGPT) {this.craftGPT = craftGPT;}

    @Override
    public String getAuthor() {
        return "zizmax";
    }

    @Override
    public String getIdentifier() {
        return "craftgpt";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params.equalsIgnoreCase("global_total_usage")) {
            return String.valueOf(craftGPT.getUsageFile().getLong("global-total-usage"));
        }

        if (params.equalsIgnoreCase("global_usage_limit")) {
            return String.valueOf(craftGPT.getConfig().getLong("global-usage-limit"));
        }

        if (params.equalsIgnoreCase("global_usage_progress")) {
            return craftGPT.getGlobalUsageProgressBar();
        }

        if (player != null) {
            if(params.equalsIgnoreCase("usage")) {
                return String.valueOf(craftGPT.getUsageFile().getLong("players." + player.getUniqueId() + ".total-usage"));
            }
            if (params.equalsIgnoreCase("usage_limit")) {
                return String.valueOf(CraftGPTListener.getTokenLimit(player));
            }
            if (params.equalsIgnoreCase("usage_progress")) {
                return craftGPT.getPlayerUsageProgressBar(player);
            }
        }

        return null; // Placeholder is unknown by the Expansion
    }
}
