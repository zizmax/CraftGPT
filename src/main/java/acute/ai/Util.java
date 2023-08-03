package acute.ai;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Util {
    private static CraftGPT craftGPT;
    public Util(CraftGPT craftGPT) {this.craftGPT = craftGPT;}
    public static boolean isAIMob(Entity entity) {
        if (craftGPT.craftGPTData.containsKey(entity.getUniqueId().toString())) return true;
        else return false;
    }

    public static AIMob getAIMob(Entity entity) {
        return craftGPT.craftGPTData.get(entity.getUniqueId().toString());
    }

    public static boolean isChatting(Player player) {
        if (craftGPT.chattingPlayers.containsKey(player.getUniqueId())) return true;
        else return false;
    }
}
