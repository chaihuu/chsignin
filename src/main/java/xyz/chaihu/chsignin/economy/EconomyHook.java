package xyz.chaihu.chsignin.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import xyz.chaihu.chsignin.sign.Wallet;

import java.util.UUID;

/**
 * Vault 经济系统接入: 签到奖励发放与补签消耗扣除。
 */
public final class EconomyHook implements Wallet {

    private Economy economy;

    /** 检测并绑定 Vault 经济提供者。 */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }
        economy = registration.getProvider();
        return economy != null;
    }

    public boolean available() {
        return economy != null;
    }

    public String format(double amount) {
        return economy == null ? String.valueOf(amount) : economy.format(amount);
    }

    @Override
    public double balance(UUID player) {
        return economy == null ? 0 : economy.getBalance(Bukkit.getOfflinePlayer(player));
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        EconomyResponse response = economy.withdrawPlayer(offline, amount);
        return response != null && response.transactionSuccess();
    }

    @Override
    public void deposit(UUID player, double amount) {
        if (economy != null) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(player), amount);
        }
    }
}
