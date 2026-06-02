package fun.gaslt.sospeso;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;

/**
 * Builds {@link TransactionInstruction}s for the deployed {@code sospeso_verifier}
 * program (mainnet, v0.1.3). Each method encodes the 8-byte Anchor discriminator
 * followed by the Borsh-serialised arguments, and lists the account metas in the
 * exact order the program's {@code #[derive(Accounts)]} structs declare them.
 *
 * <p>The program exposes sixteen instructions:
 * <ul>
 *   <li>Escrow core: {@code create_sospeso}, {@code claim_sospeso}, {@code top_up}, {@code reclaim}.</li>
 *   <li>Metadata: {@code set_meta}, {@code update_meta}, {@code clear_meta}.</li>
 *   <li>Expiry: {@code extend_expiry}.</li>
 *   <li>Registry: {@code init_registry}, {@code register_pool}, {@code sync_pool_stats}, {@code emit_version}.</li>
 *   <li>Bridge: {@code register_bridge}, {@code update_bridge}, {@code revoke_bridge}.</li>
 *   <li>Bridge pulse: {@code bridge_pulse}.</li>
 * </ul>
 */
public final class SospesoInstructions {

    /** Fixed on-chain length of {@code Bridge.kind} ({@code [u8; 16]}). */
    public static final int BRIDGE_KIND_LEN = 16;
    /** Fixed on-chain length of {@code Bridge.endpoint} ({@code [u8; 160]}). */
    public static final int BRIDGE_ENDPOINT_LEN = 160;
    /** Fixed on-chain length of {@code Bridge.label} ({@code [u8; 48]}). */
    public static final int BRIDGE_LABEL_LEN = 48;

    private SospesoInstructions() {
    }

    // ------------------------------------------------------------------
    // Escrow core
    // ------------------------------------------------------------------

    /**
     * Open a sospeso pool, funding it with {@code params.lamportsTotal()} from the
     * sponsor. The pool PDA is derived from {@code (sponsor, nonce)}.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool PDA (writable),
     * system program (readonly).
     */
    public static TransactionInstruction createSospeso(SospesoParams params, long nonce) {
        PublicKey pool = Pda.sospeso(params.sponsor(), nonce).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("create_sospeso"))
                .u64(nonce)
                .u64(params.lamportsTotal())
                .u64(params.maxPerClaim())
                .u32(params.maxClaims())
                .i64(params.expiryTs())
                .bool(params.newWalletOnly())
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(params.sponsor(), true, true),
                new AccountMeta(pool, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Draw {@code amount} lamports from {@code pool} for {@code beneficiary},
     * recording a claim receipt. {@code payer} signs and funds the receipt rent
     * (it may be the beneficiary itself or a relayer paying on its behalf).
     *
     * <p>Accounts (in order): payer (signer, writable), beneficiary (writable),
     * pool (writable), receipt PDA (writable), system program (readonly).
     */
    public static TransactionInstruction claimSospeso(
            PublicKey payer, PublicKey beneficiary, PublicKey pool, long amount) {
        PublicKey receipt = Pda.claimReceipt(pool, beneficiary).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("claim_sospeso"))
                .u64(amount)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(payer, true, true),
                new AccountMeta(beneficiary, false, true),
                new AccountMeta(pool, false, true),
                new AccountMeta(receipt, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Add {@code amount} lamports to an existing pool. Only the original sponsor
     * may top up (the program enforces the address constraint).
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (writable),
     * system program (readonly).
     */
    public static TransactionInstruction topUp(PublicKey sponsor, PublicKey pool, long amount) {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("top_up"))
                .u64(amount)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * After expiry, sweep the pool's remaining claimable lamports back to the
     * sponsor. Only the sponsor may reclaim.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (writable).
     */
    public static TransactionInstruction reclaim(PublicKey sponsor, PublicKey pool) {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("reclaim"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    /**
     * Attach descriptive metadata to a pool, creating its {@code ["meta", pool]}
     * PDA. Only the pool sponsor may call this.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (readonly),
     * meta PDA (writable), system program (readonly).
     */
    public static TransactionInstruction setMeta(
            PublicKey sponsor, PublicKey pool, String label, String description, String uri, int category) {
        PublicKey meta = Pda.meta(pool).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("set_meta"))
                .string(label)
                .string(description)
                .string(uri)
                .u8(category)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, false),
                new AccountMeta(meta, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Overwrite a pool's existing metadata. The meta account is fixed-size, so no
     * reallocation occurs.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (readonly),
     * meta PDA (writable).
     */
    public static TransactionInstruction updateMeta(
            PublicKey sponsor, PublicKey pool, String label, String description, String uri, int category) {
        PublicKey meta = Pda.meta(pool).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("update_meta"))
                .string(label)
                .string(description)
                .string(uri)
                .u8(category)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, false),
                new AccountMeta(meta, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Close a pool's metadata account, refunding its rent to the sponsor.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (readonly),
     * meta PDA (writable, closed).
     */
    public static TransactionInstruction clearMeta(PublicKey sponsor, PublicKey pool) {
        PublicKey meta = Pda.meta(pool).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("clear_meta"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, false),
                new AccountMeta(meta, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    // ------------------------------------------------------------------
    // Expiry
    // ------------------------------------------------------------------

    /**
     * Push a pool's expiry further into the future. The new expiry must be both in
     * the future and strictly later than the current one.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (writable).
     */
    public static TransactionInstruction extendExpiry(PublicKey sponsor, PublicKey pool, long newExpiryTs) {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("extend_expiry"))
                .i64(newExpiryTs)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(sponsor, true, true),
                new AccountMeta(pool, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    // ------------------------------------------------------------------
    // Registry
    // ------------------------------------------------------------------

    /**
     * Initialise the singleton {@code ["registry"]} aggregate. Permissionless: the
     * caller is recorded as the (informational) authority.
     *
     * <p>Accounts (in order): payer (signer, writable), registry PDA (writable),
     * system program (readonly).
     */
    public static TransactionInstruction initRegistry(PublicKey payer) {
        PublicKey registry = Pda.registry().getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("init_registry"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(payer, true, true),
                new AccountMeta(registry, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Fold a pool into the registry aggregate for the first time, creating its
     * {@code ["regentry", pool]} entry (which blocks double-counting).
     *
     * <p>Accounts (in order): payer (signer, writable), registry PDA (writable),
     * pool (readonly), entry PDA (writable), system program (readonly).
     */
    public static TransactionInstruction registerPool(PublicKey payer, PublicKey pool) {
        PublicKey registry = Pda.registry().getAddress();
        PublicKey entry = Pda.registryEntry(pool).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("register_pool"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(payer, true, true),
                new AccountMeta(registry, false, true),
                new AccountMeta(pool, false, false),
                new AccountMeta(entry, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Re-sync a registered pool's lamport/claim deltas into the registry
     * aggregate. The program requires no signer for this instruction.
     *
     * <p>Accounts (in order): registry PDA (writable), pool (readonly),
     * entry PDA (writable).
     */
    public static TransactionInstruction syncPoolStats(PublicKey pool) {
        PublicKey registry = Pda.registry().getAddress();
        PublicKey entry = Pda.registryEntry(pool).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("sync_pool_stats"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(registry, false, true),
                new AccountMeta(pool, false, false),
                new AccountMeta(entry, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Emit the live on-chain program version as an event.
     *
     * <p>Accounts (in order): caller (signer, readonly).
     */
    public static TransactionInstruction emitVersion(PublicKey caller) {
        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("emit_version"))
                .toByteArray();

        List<AccountMeta> keys = List.of(new AccountMeta(caller, true, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    // ------------------------------------------------------------------
    // Bridge
    // ------------------------------------------------------------------

    /**
     * Register a bridge linking the program to an off-chain service, creating the
     * {@code ["bridge", authority]} PDA. The {@code kind}, {@code endpoint}, and
     * {@code label} are encoded as NUL-padded fixed-size ascii arrays
     * ({@code [u8; 16]}, {@code [u8; 160]}, {@code [u8; 48]}).
     *
     * <p>Accounts (in order): authority (signer, writable), bridge PDA (writable),
     * system program (readonly).
     */
    public static TransactionInstruction registerBridge(
            PublicKey authority, String kind, String endpoint, String label) {
        PublicKey bridge = Pda.bridge(authority).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("register_bridge"))
                .fixedBytes(ascii(kind), BRIDGE_KIND_LEN)
                .fixedBytes(ascii(endpoint), BRIDGE_ENDPOINT_LEN)
                .fixedBytes(ascii(label), BRIDGE_LABEL_LEN)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(authority, true, true),
                new AccountMeta(bridge, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Update a bridge's endpoint, label, and enabled flag. Only the bridge
     * authority may call this.
     *
     * <p>Accounts (in order): authority (signer, writable), bridge PDA (writable).
     */
    public static TransactionInstruction updateBridge(
            PublicKey authority, String endpoint, String label, boolean enabled) {
        PublicKey bridge = Pda.bridge(authority).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("update_bridge"))
                .fixedBytes(ascii(endpoint), BRIDGE_ENDPOINT_LEN)
                .fixedBytes(ascii(label), BRIDGE_LABEL_LEN)
                .bool(enabled)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(authority, true, true),
                new AccountMeta(bridge, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    /**
     * Revoke a bridge, closing its account and refunding rent to the authority.
     *
     * <p>Accounts (in order): authority (signer, writable), bridge PDA (writable, closed).
     */
    public static TransactionInstruction revokeBridge(PublicKey authority) {
        PublicKey bridge = Pda.bridge(authority).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("revoke_bridge"))
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(authority, true, true),
                new AccountMeta(bridge, false, true));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    // ------------------------------------------------------------------
    // Bridge pulse
    // ------------------------------------------------------------------

    /**
     * Stamp the bridge's on-chain pulse, recording a fresh liveness timestamp,
     * folding {@code relayedDelta} into the cumulative relayed-claims counter, and
     * reporting the service {@code version}. The {@code ["pulse", bridge]} PDA is
     * created on the first call (the program uses {@code init_if_needed}) and
     * updated thereafter. Only the bridge authority may call this.
     *
     * <p>Accounts (in order): authority (signer, writable), bridge PDA (readonly),
     * pulse PDA (writable), system program (readonly).
     */
    public static TransactionInstruction bridgePulse(
            PublicKey authority, long relayedDelta, long version) {
        PublicKey bridge = Pda.bridge(authority).getAddress();
        PublicKey pulse = Pda.bridgePulse(bridge).getAddress();

        byte[] data = new BorshWriter()
                .bytes(SospesoProgram.instructionDiscriminator("bridge_pulse"))
                .u64(relayedDelta)
                .u32(version)
                .toByteArray();

        List<AccountMeta> keys = List.of(
                new AccountMeta(authority, true, true),
                new AccountMeta(bridge, false, false),
                new AccountMeta(pulse, false, true),
                new AccountMeta(SospesoProgram.SYSTEM_PROGRAM_ID, false, false));

        return new TransactionInstruction(SospesoProgram.PROGRAM_ID, keys, data);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
