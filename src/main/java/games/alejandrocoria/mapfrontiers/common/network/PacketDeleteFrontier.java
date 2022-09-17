package games.alejandrocoria.mapfrontiers.common.network;

import games.alejandrocoria.mapfrontiers.MapFrontiers;
import games.alejandrocoria.mapfrontiers.common.FrontierData;
import games.alejandrocoria.mapfrontiers.common.FrontiersManager;
import games.alejandrocoria.mapfrontiers.common.settings.FrontierSettings;
import games.alejandrocoria.mapfrontiers.common.settings.SettingsUser;
import games.alejandrocoria.mapfrontiers.common.settings.SettingsUserShared;
import games.alejandrocoria.mapfrontiers.common.util.UUIDHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

@ParametersAreNonnullByDefault
public class PacketDeleteFrontier {
    private final UUID frontierID;

    public PacketDeleteFrontier(UUID frontierID) {
        this.frontierID = frontierID;
    }

    public static PacketDeleteFrontier fromBytes(FriendlyByteBuf buf) {
        return new PacketDeleteFrontier(UUIDHelper.fromBytes(buf));
    }

    public static void toBytes(PacketDeleteFrontier packet, FriendlyByteBuf buf) {
        UUIDHelper.toBytes(buf, packet.frontierID);
    }

    public static void handle(PacketDeleteFrontier message, MinecraftServer server, ServerPlayer player) {
        server.execute(() -> {
            SettingsUser playerUser = new SettingsUser(player);
            FrontierData frontier = FrontiersManager.instance.getFrontierFromID(message.frontierID);

            if (frontier != null) {
                if (frontier.getPersonal()) {
                    if (frontier.getOwner().equals(playerUser)) {
                        boolean deleted = FrontiersManager.instance.deletePersonalFrontier(frontier.getOwner(),
                                frontier.getDimension(), frontier.getId());
                        if (deleted) {
                            if (frontier.getUsersShared() != null) {
                                for (SettingsUserShared userShared : frontier.getUsersShared()) {
                                    FrontiersManager.instance.deletePersonalFrontier(userShared.getUser(),
                                            frontier.getDimension(), frontier.getId());
                                }
                            }
                            PacketHandler.sendToUsersWithAccess(PacketFrontierDeleted.class, new PacketFrontierDeleted(frontier.getDimension(),
                                    frontier.getId(), frontier.getPersonal(), player.getId()), frontier, server);
                        }
                    } else {
                        frontier.removeUserShared(playerUser);
                        FrontiersManager.instance.deletePersonalFrontier(playerUser, frontier.getDimension(),
                                frontier.getId());

                        PacketHandler.sendTo(PacketFrontierDeleted.class, new PacketFrontierDeleted(frontier.getDimension(), frontier.getId(),
                                frontier.getPersonal(), player.getId()), player);
                        PacketHandler.sendToUsersWithAccess(PacketFrontierUpdated.class, new PacketFrontierUpdated(frontier, player.getId()),
                                frontier, server);

                        frontier.removeChange(FrontierData.Change.Shared);
                    }

                    return;
                } else {
                    if (FrontiersManager.instance.getSettings().checkAction(FrontierSettings.Action.DeleteGlobalFrontier, playerUser,
                            MapFrontiers.isOPorHost(player), frontier.getOwner())) {
                        boolean deleted = FrontiersManager.instance.deleteGlobalFrontier(frontier.getDimension(),
                                frontier.getId());
                        if (deleted) {
                            PacketHandler.sendToAll(PacketFrontierDeleted.class, new PacketFrontierDeleted(frontier.getDimension(), frontier.getId(),
                                    frontier.getPersonal(), player.getId()), server);
                        }

                        return;
                    }
                }

                PacketHandler.sendTo(PacketSettingsProfile.class, new PacketSettingsProfile(FrontiersManager.instance.getSettings().getProfile(player)), player);
            }
        });
    }
}
