package fi.solita.utils.concurrency;

import reactor.blockhound.BlockingOperationError;

public final class BlockingError extends Error {
    BlockingError(BlockingOperationError original) {
        super(original.getMessage(), original);
    }
    BlockingError(BlockingError original) {
        super(original.getMessage(), original);
    }
}