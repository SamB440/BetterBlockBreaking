package com.github.cheesesoftware.betterblockbreaking.task;

import com.github.cheesesoftware.betterblockbreaking.BetterBlockBreaking;
import com.github.cheesesoftware.betterblockbreaking.block.DamageBlock;
import com.github.cheesesoftware.betterblockbreaking.util.ReflectiveUtils;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class RemoveOldDamagedBlocksTask extends BukkitRunnable {

    private final BetterBlockBreaking plugin;
    public static long millisecondsBeforeBeginFade = 120000;
    public static long millisecondsBetweenFade = 2000;
    public static int damageDecreasePerFade = 1;

    public RemoveOldDamagedBlocksTask(BetterBlockBreaking plugin) {
	this.plugin = plugin;
    }

    @Override
    public void run() {
		HashMap<Location, DamageBlock> damagedBlocks = new HashMap<>(this.plugin.damageBlocks);

		Iterator<Entry<Location, DamageBlock>> it = damagedBlocks.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Location, DamageBlock> current = it.next();
			DamageBlock damageBlock = current.getValue();
			Date dateModified = damageBlock.getDamageDate();
			Date dateLastFade = damageBlock.getLastFadeDate();
			boolean remove = false;

			if (dateLastFade != null) {
                long elapsedMilliseconds = (new Date().getTime()) - dateLastFade.getTime();
                if (elapsedMilliseconds >= millisecondsBetweenFade) {
                    damageBlock.setLastFadeDate();
                    remove = this.fadeBlock(damageBlock);
                }
			} else if (dateModified != null) {
                long elapsedMilliseconds = (new Date().getTime()) - dateModified.getTime();
                if (elapsedMilliseconds >= millisecondsBeforeBeginFade) {
                    damageBlock.setLastFadeDate();
                    remove = this.fadeBlock(damageBlock);
                }
			} else remove = true;

			if (remove) it.remove();
		}

		this.plugin.damageBlocks = damagedBlocks;
    }

    private boolean fadeBlock(DamageBlock damageBlock) {
        if (damageBlock.isDamaged()) {
            float damage = damageBlock.getDamage();
            damage -= damageDecreasePerFade;
            if (damage <= 0) {
                damageBlock.removeAllDamage();
                return true;
            }

            damageBlock.setDamage(damage, null);
            if (damageBlock.getEntity() != null) {
                ReflectiveUtils.sendDamagePacket(damageBlock, (int) damage);
            } else return true;
        } else return true;
        return false;
    }
}
