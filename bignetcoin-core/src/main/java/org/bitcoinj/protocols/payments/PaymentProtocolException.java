/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.protocols.payments;

import java.security.cert.X509Certificate;
import java.util.List;

public class PaymentProtocolException extends Exception {
    public PaymentProtocolException(String msg) {
        super(msg);
    }

    public PaymentProtocolException(Exception e) {
        super(e);
    }

    public static class Expired extends PaymentProtocolException {
        public Expired(String msg) {
            super(msg);
        }
    }

    public static class InvalidPaymentRequestURL extends PaymentProtocolException {
        public InvalidPaymentRequestURL(String msg) {
            super(msg);
        }

        public InvalidPaymentRequestURL(Exception e) {
            super(e);
        }
    }

    public static class InvalidPaymentURL extends PaymentProtocolException {
        public InvalidPaymentURL(Exception e) {
            super(e);
        }

        public InvalidPaymentURL(String msg) {
            super(msg);
        }
    }

    public static class InvalidOutputs extends PaymentProtocolException {
        public InvalidOutputs(String msg) {
            super(msg);
        }
    }

    public static class InvalidVersion extends PaymentProtocolException {
        public InvalidVersion(String msg) {
            super(msg);
        }
    }

    public static class InvalidNetwork extends PaymentProtocolException {
        public InvalidNetwork(String msg) {
            super(msg);
        }
    }

    public static class InvalidPkiType extends PaymentProtocolException {
        public InvalidPkiType(String msg) {
            super(msg);
        }
    }

    public static class InvalidPkiData extends PaymentProtocolException {
        public InvalidPkiData(String msg) {
            super(msg);
        }

        public InvalidPkiData(Exception e) {
            super(e);
        }
    }

    public static class PkiVerificationException extends PaymentProtocolException {
        public List<X509Certificate> certificates;

        public PkiVerificationException(String msg) {
            super(msg);
        }

        public PkiVerificationException(Exception e) {
            super(e);
        }

        public PkiVerificationException(Exception e, List<X509Certificate> certificates) {
            super(e);
            this.certificates = certificates;
        }
    }
}
