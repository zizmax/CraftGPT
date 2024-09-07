package acute.ai;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;

import java.util.Random;

public class EntityMountEventListener implements  org.bukkit.event.Listener {

    // EntityMountEvent was moved from the Spigot to the Bukkit package in 1.20.5/1.20.6.
    // This event is pulled out and registered separately to maintain backwards compatibility.

    private static CraftGPT craftGPT;
    private static CraftGPTListener craftGPTListener;

    public EntityMountEventListener(CraftGPT craftGPT, CraftGPTListener craftGPTListener) {
        this.craftGPT = craftGPT;
        this.craftGPTListener = craftGPTListener;
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            String name = "player-mount";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getMount().getType().toString().toLowerCase());
            craftGPTListener.handlePlayerEventReaction((Player) event.getEntity(), name, eventMessage);
        }
    }

}
