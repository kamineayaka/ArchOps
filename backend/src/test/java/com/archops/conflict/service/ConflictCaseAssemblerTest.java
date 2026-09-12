package com.archops.conflict.service;

import com.archops.conflict.domain.ConflictStatus;
import com.archops.conflict.dto.ConflictCaseResponse;
import com.archops.curated.domain.CuratedObject;
import com.archops.curated.domain.CuratedRelationType;
import com.archops.observed.domain.ObservedAvailability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictCaseAssemblerTest {

    @Test
    void hollowObservedTrackIsHollowNotStaleHost() {
        CuratedObject staleHost = host("host-stale", "stale");

        ConflictCaseResponse.TrackValue value = ConflictCaseAssembler.observedTrackValue(
                true, false, ObservedAvailability.PRESENT, staleHost, "host-stale");

        assertThat(value).isEqualTo(ConflictCaseResponse.TrackValue.hollow());
        assertThat(value.availability()).isEqualTo("HOLLOW");
        assertThat(value.hostId()).isNull();
        assertThat(value.hostName()).isNull();
    }

    @Test
    void identityLostObservedTrackIsIdentityLostNotPresent() {
        CuratedObject residualHost = host("host-residual", "residual");

        ConflictCaseResponse.TrackValue value = ConflictCaseAssembler.observedTrackValue(
                false, true, ObservedAvailability.PRESENT, residualHost, "host-residual");

        assertThat(value).isEqualTo(ConflictCaseResponse.TrackValue.identityLost());
        assertThat(value.availability()).isEqualTo("IDENTITY_LOST");
        assertThat(value.hostId()).isNull();
    }

    @Test
    void absentObservedTrackIsAbsent() {
        ConflictCaseResponse.TrackValue value = ConflictCaseAssembler.observedTrackValue(
                false, false, ObservedAvailability.ABSENT, null, null);

        assertThat(value).isEqualTo(ConflictCaseResponse.TrackValue.absent());
        assertThat(value.availability()).isEqualTo("ABSENT");
    }

    @Test
    void presentObservedTrackUsesHostIdAndName() {
        CuratedObject observedHost = host("host-obs", "obs-name");

        ConflictCaseResponse.TrackValue value = ConflictCaseAssembler.observedTrackValue(
                false, false, ObservedAvailability.PRESENT, observedHost, "host-obs");

        assertThat(value).isEqualTo(ConflictCaseResponse.TrackValue.present("host-obs", "obs-name"));
    }

    @Test
    void presentFallsBackToRowTargetWhenHostRowMissing() {
        ConflictCaseResponse.TrackValue value = ConflictCaseAssembler.observedTrackValue(
                false, false, ObservedAvailability.PRESENT, null, "host-missing");

        assertThat(value).isEqualTo(ConflictCaseResponse.TrackValue.present("host-missing", null));
    }

    @Test
    void statusViewMapsEachConflictStatus() {
        assertThat(ConflictCaseAssembler.statusView(ConflictStatus.OPEN))
                .isEqualTo(ConflictCaseResponse.ConflictStatusView.OPEN);
        assertThat(ConflictCaseAssembler.statusView(ConflictStatus.PENDING_CLOSE))
                .isEqualTo(ConflictCaseResponse.ConflictStatusView.PENDING_CLOSE);
        assertThat(ConflictCaseAssembler.statusView(ConflictStatus.CLOSED))
                .isEqualTo(ConflictCaseResponse.ConflictStatusView.CLOSED);
        assertThat(ConflictCaseAssembler.statusView(ConflictStatus.SUSPENDED))
                .isEqualTo(ConflictCaseResponse.ConflictStatusView.SUSPENDED);
    }

    @Test
    void mergeKeyUsesSubjectRelationAndZhLabel() {
        assertThat(ConflictCaseAssembler.mergeKey("ctr-1", CuratedRelationType.RUNS_ON))
                .isEqualTo(new ConflictCaseResponse.MergeKey("ctr-1", CuratedRelationType.RUNS_ON, "运行于"));
    }

    private static CuratedObject host(String id, String name) {
        CuratedObject host = new CuratedObject();
        host.setId(id);
        host.setName(name);
        return host;
    }
}
