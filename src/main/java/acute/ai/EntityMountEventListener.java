package acute.ai;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;

public class EntityMountEventListener implements  org.bukkit.event.Listener {

    //FIXME: There's something weird going on with this event.
    // It's worked fine in testing to-date but all of a sudden people are reporting issues with it causing the events to not register
    // Someone else appears to have the same issue and there was no resolution: https://www.spigotmc.org/threads/entitymountevent-work-around.637052/
    // No idea what's actually going on, so this is the hacky workaround
    private static CraftGPT craftGPT;
    private static CraftGPTListener craftGPTListener;

    public EntityMountEventListener(CraftGPT craftGPT) {
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && !event.isCancelled()) {
            event.getEntity().sendMessage("mounted");
            String name = "player-mount";
            String rawMessage = craftGPT.getConfig().getString("events." + name + ".message");
            String eventMessage = String.format(rawMessage, event.getMount().getType().toString().toLowerCase());
            craftGPTListener.handlePlayerEventReaction((Player) event.getEntity(), name, eventMessage);
        }
    }

}
