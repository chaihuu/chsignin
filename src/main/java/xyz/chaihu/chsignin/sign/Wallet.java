package xyz.chaihu.chsignin.sign;

import java.util.UUID;

/**
 * 经济操作抽象, 便于核心逻辑与 Bukkit/Vault 解耦并支持单元测试。
 */
public interface Wallet {

    double balance(UUID player);

    boolean withdraw(UUID player, double amount);

    void deposit(UUID player, double amount);
}
