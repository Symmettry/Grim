// This file was designed and is an original check for GrimAC
// Copyright (C) 2021 DefineOutside
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
package ac.grim.grimac.utils.data;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.nmsutil.BoundingBoxSize;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;

// You may not copy the check unless you are licensed under GPL
public class ReachInterpolationData {

    private final SimpleCollisionBox targetLocation;
    private SimpleCollisionBox startingLocation;
    private int interpolationStepsLowBound = 0;
    private int interpolationStepsHighBound = 0;
    private int interpolationSteps = 1;

    public ReachInterpolationData(final GrimPlayer player, final SimpleCollisionBox startingLocation,
                                  final TrackedPosition position, final PacketEntity entity) {
        final boolean isPointNine = !player.compensatedEntities.getSelf().inVehicle() && player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9);

        this.startingLocation = startingLocation;
        final Vector3d pos = position.getPos();
        this.targetLocation = GetBoundingBox.getBoundingBoxFromPosAndSize(entity, pos.x, pos.y, pos.z, BoundingBoxSize.getWidth(player, entity), BoundingBoxSize.getHeight(player, entity));

        // 1.9 -> 1.8 precision loss in packets
        // (ViaVersion is doing some stuff that makes this code difficult)
        if (!isPointNine && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
            targetLocation.expand(0.03125);
        }

        if (EntityTypes.isTypeInstanceOf(entity.type, EntityTypes.BOAT)) {
            interpolationSteps = 10;
        } else if (entity.isMinecart()) {
            interpolationSteps = 5;
        } else if (entity.type == EntityTypes.SHULKER) {
            interpolationSteps = 1;
        } else if (entity.isLivingEntity()) {
            interpolationSteps = 3;
        } else {
            interpolationSteps = 1;
        }

        if (isPointNine) interpolationStepsHighBound = getInterpolationSteps();
    }

    // While riding entities, there is no interpolation.
    public ReachInterpolationData(final SimpleCollisionBox finishedLoc) {
        this.startingLocation = finishedLoc;
        this.targetLocation = finishedLoc;
    }

    private int getInterpolationSteps() {
        return interpolationSteps;
    }

    public static SimpleCollisionBox combineCollisionBox(final SimpleCollisionBox one, final SimpleCollisionBox two) {
        final double minX = Math.min(one.minX, two.minX);
        final double maxX = Math.max(one.maxX, two.maxX);
        final double minY = Math.min(one.minY, two.minY);
        final double maxY = Math.max(one.maxY, two.maxY);
        final double minZ = Math.min(one.minZ, two.minZ);
        final double maxZ = Math.max(one.maxZ, two.maxZ);
        return new SimpleCollisionBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // To avoid huge branching when bruteforcing interpolation -
    // we combine the collision boxes for the steps.
    //
    // Designed around being unsure of minimum interp, maximum interp, and target location on 1.9 clients
    public SimpleCollisionBox getPossibleLocationCombined() {
        final int interpSteps = getInterpolationSteps();
        final double stepMinX = (targetLocation.minX - startingLocation.minX) / (double) interpSteps;
        final double stepMaxX = (targetLocation.maxX - startingLocation.maxX) / (double) interpSteps;
        final double stepMinY = (targetLocation.minY - startingLocation.minY) / (double) interpSteps;
        final double stepMaxY = (targetLocation.maxY - startingLocation.maxY) / (double) interpSteps;
        final double stepMinZ = (targetLocation.minZ - startingLocation.minZ) / (double) interpSteps;
        final double stepMaxZ = (targetLocation.maxZ - startingLocation.maxZ) / (double) interpSteps;

        SimpleCollisionBox minimumInterpLocation = new SimpleCollisionBox(
                startingLocation.minX + (interpolationStepsLowBound * stepMinX),
                startingLocation.minY + (interpolationStepsLowBound * stepMinY),
                startingLocation.minZ + (interpolationStepsLowBound * stepMinZ),
                startingLocation.maxX + (interpolationStepsLowBound * stepMaxX),
                startingLocation.maxY + (interpolationStepsLowBound * stepMaxY),
                startingLocation.maxZ + (interpolationStepsLowBound * stepMaxZ));

        for (int step = interpolationStepsLowBound + 1; step <= interpolationStepsHighBound; step++) {
            minimumInterpLocation = combineCollisionBox(minimumInterpLocation, new SimpleCollisionBox(
                    startingLocation.minX + (step * stepMinX),
                    startingLocation.minY + (step * stepMinY),
                    startingLocation.minZ + (step * stepMinZ),
                    startingLocation.maxX + (step * stepMaxX),
                    startingLocation.maxY + (step * stepMaxY),
                    startingLocation.maxZ + (step * stepMaxZ)));
        }

        return minimumInterpLocation;
    }

    public void updatePossibleStartingLocation(final SimpleCollisionBox possibleLocationCombined) {
        //GrimAC.staticGetLogger().info(ChatColor.BLUE + "Updated new starting location as second trans hasn't arrived " + startingLocation);
        this.startingLocation = combineCollisionBox(startingLocation, possibleLocationCombined);
        //GrimAC.staticGetLogger().info(ChatColor.BLUE + "Finished updating new starting location as second trans hasn't arrived " + startingLocation);
    }

    public void tickMovement(final boolean incrementLowBound, final boolean tickingReliably) {
        if (!tickingReliably) this.interpolationStepsHighBound = getInterpolationSteps();
        if (incrementLowBound)
            this.interpolationStepsLowBound = Math.min(interpolationStepsLowBound + 1, getInterpolationSteps());
        this.interpolationStepsHighBound = Math.min(interpolationStepsHighBound + 1, getInterpolationSteps());
    }

    @Override
    public String toString() {
        return "ReachInterpolationData{" +
                "targetLocation=" + targetLocation +
                ", startingLocation=" + startingLocation +
                ", interpolationStepsLowBound=" + interpolationStepsLowBound +
                ", interpolationStepsHighBound=" + interpolationStepsHighBound +
                '}';
    }

}