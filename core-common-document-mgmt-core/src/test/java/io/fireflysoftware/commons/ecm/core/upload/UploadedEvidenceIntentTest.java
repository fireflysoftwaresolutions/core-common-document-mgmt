/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadRequest;
import io.fireflysoftware.commons.ecm.interfaces.upload.validation.impl.BoundedEvidenceContentValidatorImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UploadedEvidenceIntentTest {
    final UUID action=UUID.fromString("7b3ca289-011d-4525-9597-a980bdfc4559"),application=UUID.fromString("756709cf-aa88-4acf-aae6-1074104483e2"),party=UUID.fromString("66f9f2ba-05c0-45d3-ac83-e13329a2660e");
    EvidenceUploadRequest request(byte[] content){return new EvidenceUploadRequest(action,application,party,"evidence.pdf","application/pdf","PAYSLIP",content);}
    @Test void digestAndLengthDescribeTheExactCapturedBytes(){
        var intent=UploadedEvidenceIntent.capture(request(new byte[]{1,2,3}));
        assertThat(intent.contentDigest()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(intent.length()).isEqualTo(3);assertThat(intent.digest()).matches("[0-9a-f]{64}");
    }
    @Test void changedEqualLengthContentChangesTheOwnerIntent(){
        assertThat(UploadedEvidenceIntent.capture(request(new byte[]{1,2,3})).digest()).isNotEqualTo(UploadedEvidenceIntent.capture(request(new byte[]{4,5,6})).digest());
    }
    @Test void captureDoesNotRetainCallerOrAccessorArrays(){
        byte[] content={1,2,3};var request=request(content);content[0]=9;var intent=UploadedEvidenceIntent.capture(request);
        request.content()[0]=8;intent.content()[0]=7;
        assertThat(intent.content()).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(intent.contentDigest()).isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
    }
    @Test void originalActionApplicationAndPartyArePartOfTheIntent(){
        var base=request(new byte[]{1});String digest=UploadedEvidenceIntent.capture(base).digest();
        for(var changed:List.of(new EvidenceUploadRequest(UUID.randomUUID(),application,party,base.fileName(),base.mediaType(),base.documentTypeCode(),base.content()),new EvidenceUploadRequest(action,UUID.randomUUID(),party,base.fileName(),base.mediaType(),base.documentTypeCode(),base.content()),new EvidenceUploadRequest(action,application,UUID.randomUUID(),base.fileName(),base.mediaType(),base.documentTypeCode(),base.content())))
            assertThat(UploadedEvidenceIntent.capture(changed).digest()).isNotEqualTo(digest);
    }
    @Test void metadataFramingAndClassificationCannotCollide(){
        var a=new EvidenceUploadRequest(action,application,party,"a:b.pdf","application/pdf","PAYSLIP",new byte[]{1});
        var b=new EvidenceUploadRequest(action,application,party,"a.pdf","application/pdf","BANK_STATEMENT",new byte[]{1});
        assertThat(UploadedEvidenceIntent.capture(a).digest()).isNotEqualTo(UploadedEvidenceIntent.capture(b).digest());
        assertThat(UploadedEvidenceIntent.capture(a).digest()).isEqualTo(UploadedEvidenceIntent.capture(a).digest());
    }
    @Test void requiresNonemptyBoundedRealContent(){
        for(byte[] content:new byte[][]{null,new byte[0],new byte[UploadedEvidenceIntent.MAX_CONTENT_BYTES+1]})
            assertThatThrownBy(()->UploadedEvidenceIntent.capture(request(content))).isInstanceOf(IllegalArgumentException.class);
        assertThat(UploadedEvidenceIntent.capture(request(new byte[UploadedEvidenceIntent.MAX_CONTENT_BYTES])).length()).isEqualTo(UploadedEvidenceIntent.MAX_CONTENT_BYTES);
        var validator=new BoundedEvidenceContentValidatorImpl();
        assertThat(validator.isValid(null,null)).isFalse();assertThat(validator.isValid(new byte[0],null)).isFalse();
        assertThat(validator.isValid(new byte[UploadedEvidenceIntent.MAX_CONTENT_BYTES],null)).isTrue();
        assertThat(validator.isValid(new byte[UploadedEvidenceIntent.MAX_CONTENT_BYTES+1],null)).isFalse();
    }
    @Test void refusesMissingIdentityAndMetadata(){
        var r=request(new byte[]{1});
        for(var changed:List.of(new EvidenceUploadRequest(null,application,party,r.fileName(),r.mediaType(),r.documentTypeCode(),r.content()),new EvidenceUploadRequest(action,new UUID(0,0),party,r.fileName(),r.mediaType(),r.documentTypeCode(),r.content()),new EvidenceUploadRequest(action,application,party," ",r.mediaType(),r.documentTypeCode(),r.content()),new EvidenceUploadRequest(action,application,party,r.fileName(),"application/pdf\r\nX: forged",r.documentTypeCode(),r.content()),new EvidenceUploadRequest(action,application,party,r.fileName(),r.mediaType(),"unresolved type",r.content())))
            assertThatThrownBy(()->UploadedEvidenceIntent.capture(changed)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void malformedUnicodeAndPathNamesCannotEnterCustody(){
        for(String name:new String[]{"a/b.pdf","a\\b.pdf","a"+(char)0,"a"+(char)0xd800,"a".repeat(256)})
            assertThatThrownBy(()->UploadedEvidenceIntent.capture(new EvidenceUploadRequest(action,application,party,name,"application/pdf","PAYSLIP",new byte[]{1}))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void diagnosticsContainNoContentOrFilename(){
        var request=new EvidenceUploadRequest(action,application,party,"private-evidence.pdf","application/pdf","PAYSLIP",new byte[]{1,2,3});
        assertThat(request.toString()).doesNotContain("private-evidence","AQID");
        assertThat(UploadedEvidenceIntent.capture(request).toString()).doesNotContain("private-evidence","AQID");
    }
}
