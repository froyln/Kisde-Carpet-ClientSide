package dev.bulletcarpet;

import dev.bulletcarpet.mixin.InventoryRenderUtilsMixin;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import sun.misc.Unsafe;

public final class InventoryCameraCheck {
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe)unsafeField.get(null);
        Field instance = Minecraft.class.getDeclaredField("instance"); instance.setAccessible(true);
        Object original = instance.get(null);
        Minecraft minecraft = (Minecraft)unsafe.allocateInstance(Minecraft.class);
        Field view = Minecraft.class.getDeclaredField("renderViewEntity"); view.setAccessible(true);
        EntityPlayerSP player = (EntityPlayerSP)unsafe.allocateInstance(EntityPlayerSP.class);
        EntityPlayerSP camera = (EntityPlayerSP)unsafe.allocateInstance(EntityPlayerSP.class);
        minecraft.player = player;
        Method select = InventoryRenderUtilsMixin.class.getDeclaredMethod("bulletcarpet$useCameraInventory",Entity.class);
        select.setAccessible(true);
        try {
            instance.set(null,minecraft);
            view.set(minecraft,camera);
            check(select.invoke(null,player) == camera,"Free camera did not replace body ray source");
            view.set(minecraft,player);
            check(select.invoke(null,player) == player,"Normal player camera changed");
            view.set(minecraft,null);
            check(select.invoke(null,player) == player,"Missing camera lost original ray source");
        } finally { instance.set(null,original); }
        System.out.println("Inventory preview active-camera, normal-camera and missing-camera checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
