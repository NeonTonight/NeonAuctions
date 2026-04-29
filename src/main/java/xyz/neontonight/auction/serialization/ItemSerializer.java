package xyz.neontonight.auction.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemSerializer {

    public String serialize(ItemStack itemStack) {
        try {
            ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream objectOutput = new BukkitObjectOutputStream(byteOutput)) {
                objectOutput.writeObject(itemStack);
            }
            return Base64.getEncoder().encodeToString(byteOutput.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize item stack", exception);
        }
    }

    public ItemStack deserialize(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            try (BukkitObjectInputStream objectInput = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                Object object = objectInput.readObject();
                if (!(object instanceof ItemStack itemStack)) {
                    throw new IllegalStateException("Serialized data is not an item stack");
                }
                return itemStack;
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Could not deserialize item stack", exception);
        }
    }

    public String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
