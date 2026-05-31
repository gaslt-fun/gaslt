package fun.gaslt.sospeso;

import java.util.List;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;

/**
 * Builds {@link TransactionInstruction}s for the four sospeso program
 * instructions. Each method encodes the 8-byte Anchor discriminator followed by
 * the Borsh-serialised arguments, and lists the account metas in the exact order
 * the program's {@code #[derive(Accounts)]} structs declare them.
 *
 * <p>The program exposes exactly four instructions on mainnet (v0.1.2):
 * {@code create_sospeso}, {@code claim_sospeso}, {@code top_up}, {@code reclaim}.
 */
public final class SospesoInstructions {

    private SospesoInstructions() {
    }

    /**
     * Open a sospeso pool, funding it with {@code params.lamportsTotal()} from the
     * sponsor. The pool PDA is derived from {@code (sponsor, nonce)}.
     *
     * <p>Accounts (in order): sponsor (signer, writable), target program
     * (readonly), pool PDA (writable), system program (readonly).
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
                new AccountMeta(params.program(), false, false),
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
     * After expiry, sweep the pool's remaining lamports back to the sponsor and
     * close the account. Only the sponsor may reclaim.
     *
     * <p>Accounts (in order): sponsor (signer, writable), pool (writable, closed).
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
}
