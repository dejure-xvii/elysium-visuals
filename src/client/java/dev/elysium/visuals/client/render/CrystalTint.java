package dev.elysium.visuals.client.render;

/** Extra field on the crystal render state: the CustomCrystal tint (-1 = none). */
public interface CrystalTint {
	int elysium$tint();

	void elysium$setTint(int tint);
}
