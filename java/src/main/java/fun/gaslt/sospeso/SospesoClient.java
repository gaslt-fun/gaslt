package fun.gaslt.sospeso;

import java.util.Objects;
import java.util.Optional;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.rpc.RpcException;
import org.p2p.solanaj.rpc.types.AccountInfo;

/**
 * A high-level read client for the sospeso program. Wraps a solanaj
 * {@link RpcClient}, fetches account data over JSON-RPC, and decodes it into the
 * {@link Sospeso} and {@link ClaimReceipt} models.
 *
 * <p>This client is read-only: it derives PDAs and reads on-chain state. Building
 * and signing transactions is the caller's responsibility using
 * {@link SospesoInstructions}; that keeps key handling outside this library.
 */
public final class SospesoClient {

    private final RpcClient rpc;

    /** Wrap an existing solanaj RPC client (e.g. {@code new RpcClient(Cluster.MAINNET)}). */
    public SospesoClient(RpcClient rpc) {
        this.rpc = Objects.requireNonNull(rpc, "rpc");
    }

    /** Convenience constructor from an RPC endpoint URL. */
    public SospesoClient(String endpoint) {
        this(new RpcClient(endpoint));
    }

    /** The underlying solanaj RPC client. */
    public RpcClient rpc() {
        return rpc;
    }

    /**
     * Fetch and decode a pool by its PDA. Returns empty if the account does not
     * exist on chain.
     */
    public Optional<Sospeso> fetchSospeso(PublicKey pda) throws RpcException {
        return fetchAccountData(pda).map(Sospeso::from);
    }

    /** Fetch and decode a pool derived from {@code (sponsor, nonce)}. */
    public Optional<Sospeso> fetchSospeso(PublicKey sponsor, long nonce) throws RpcException {
        return fetchSospeso(Pda.sospeso(sponsor, nonce).getAddress());
    }

    /**
     * Fetch and decode the claim receipt for {@code (pool, beneficiary)}, if any.
     * Its presence means the beneficiary has already claimed from this pool.
     */
    public Optional<ClaimReceipt> fetchClaimReceipt(PublicKey pool, PublicKey beneficiary)
            throws RpcException {
        PublicKey receipt = Pda.claimReceipt(pool, beneficiary).getAddress();
        return fetchAccountData(receipt).map(ClaimReceipt::from);
    }

    /**
     * Whether {@code beneficiary} has already claimed from {@code pool}, i.e.
     * whether the claim-receipt PDA exists.
     */
    public boolean hasClaimed(PublicKey pool, PublicKey beneficiary) throws RpcException {
        return fetchClaimReceipt(pool, beneficiary).isPresent();
    }

    private Optional<byte[]> fetchAccountData(PublicKey account) throws RpcException {
        AccountInfo info = rpc.getApi().getAccountInfo(account);
        if (info == null || info.getValue() == null) {
            return Optional.empty();
        }
        byte[] data = info.getDecodedData();
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        return Optional.of(data);
    }
}
