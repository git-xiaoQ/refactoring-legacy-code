package cn.xpbootcamp.legacy_code;

import cn.xpbootcamp.legacy_code.enums.STATUS;
import cn.xpbootcamp.legacy_code.service.WalletService;
import cn.xpbootcamp.legacy_code.service.WalletServiceImpl;
import cn.xpbootcamp.legacy_code.utils.IdGenerator;
import cn.xpbootcamp.legacy_code.utils.RedisDistributedLock;

import javax.transaction.InvalidTransactionException;

public class WalletTransaction {
    private String id;
    private Long buyerId;
    private Long sellerId;
    private Long productId;
    private String orderId;
    private Long createdTimestamp;
    private Double amount;
    private int days;
    final static int DAYS =1728000000;

    public void updateStatus(STATUS status) {
        this.status = status;
    }

    private STATUS status;
    private String walletTransactionId;


    public WalletTransaction(String preAssignedId, Long buyerId, Long sellerId, Long productId, String orderId) {
        if (preAssignedId != null && !preAssignedId.isEmpty()) {
            this.id = preAssignedId;
        } else {
            this.id = IdGenerator.generateTransactionId();
        }
        if (!this.id.startsWith("t_")) {
            this.id = "t_" + preAssignedId;
        }
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.productId = productId;
        this.orderId = orderId;
        this.status = STATUS.TO_BE_EXECUTED;
        this.createdTimestamp = System.currentTimeMillis();
    }

    public boolean execute() throws InvalidTransactionException {
        if (isInvalidTransaction()) {
            throw new InvalidTransactionException("This is an invalid transaction");
        }
        if (isExpired()) return true;
        try {
            // 锁定未成功，返回false
            if (!isLocked(id)) {
                return false;
            }

            if (status == STATUS.EXECUTED) return true; // double check

            // 交易超过20天
            if (isOutOf(DAYS)) {
                updateStatus(STATUS.EXPIRED);
                return false;
            }
            WalletService walletService = new WalletServiceImpl();

            String walletTransactionId = walletService.moveMoney(id, buyerId, sellerId, amount);
            if (walletTransactionId != null) {
                this.walletTransactionId = walletTransactionId;
                updateStatus(STATUS.EXECUTED);
                return true;
            } else {
                updateStatus(STATUS.FAILED);
                return false;
            }
        } finally {
            unlock(id);
        }
    }

    private boolean isInvalidTransaction() {
        return buyerId == null || (sellerId == null || amount < 0.0);
    }

    private boolean isExpired() {
        return status == STATUS.EXECUTED;
    }
    private boolean isOutOf(int days) {
        long executionInvokedTimestamp = System.currentTimeMillis();
        return  executionInvokedTimestamp - createdTimestamp >days ;
    }

    private boolean isLocked(String id) {
        RedisDistributedLock redisDistributedLock = RedisDistributedLock.getSingletonInstance();
        return redisDistributedLock.lock(id);
    }

    private void unlock(String id) {
        RedisDistributedLock redisDistributedLock = RedisDistributedLock.getSingletonInstance();
        if (isLocked(id)) {
            redisDistributedLock.unlock(id);
        }
    }

}