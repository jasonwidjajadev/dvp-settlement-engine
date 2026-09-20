package com.jasonwidjaja.dvp.application;

class SuccessfulSettlementNotImplementedException extends UnsupportedOperationException {

    SuccessfulSettlementNotImplementedException() {
        super("Successful settlement writes belong to Section 3.6");
    }
}
