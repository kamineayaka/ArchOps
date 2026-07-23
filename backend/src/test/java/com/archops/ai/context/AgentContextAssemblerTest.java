package com.archops.ai.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archops.ai.dto.UiContext;
import com.archops.ai.provider.domain.PlatformAiSettings;
import com.archops.ai.provider.service.PlatformAiSettingsService;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.dto.AssetResponse;
import com.archops.asset.service.AssetService;
import com.archops.knowledge.architecture.ArchitectureProperties;
import com.archops.knowledge.architecture.dto.ArchitectureViewResponse;
import com.archops.knowledge.architecture.service.ArchitectureViewService;
import com.archops.knowledge.domain.KnowledgeSourceType;
import com.archops.knowledge.domain.WorkLog;
import com.archops.knowledge.repository.WorkLogRepository;
import com.archops.knowledge.retrieval.RagRetrievalService;
import com.archops.knowledge.retrieval.RagScope;
import com.archops.knowledge.retrieval.ScoredChunk;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AgentContextAssemblerTest {

    private AssetService assetService;
    private RagRetrievalService ragRetrievalService;
    private PlatformAiSettingsService settingsService;
    private ArchitectureViewService architectureViewService;
    private ArchitectureProperties architectureProperties;
    private WorkLogRepository workLogRepository;
    private AgentContextAssembler assembler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        assetService = mock(AssetService.class);
        ragRetrievalService = mock(RagRetrievalService.class);
        settingsService = mock(PlatformAiSettingsService.class);
        architectureViewService = mock(ArchitectureViewService.class);
        architectureProperties = new ArchitectureProperties();
        architectureProperties.setContextMaxChars(4000);
        workLogRepository = mock(WorkLogRepository.class);

        ObjectProvider<ArchitectureViewService> viewProvider = mock(ObjectProvider.class);
        ObjectProvider<ArchitectureProperties> propsProvider = mock(ObjectProvider.class);
        when(viewProvider.getIfAvailable()).thenReturn(architectureViewService);
        when(propsProvider.stream()).thenReturn(java.util.stream.Stream.of(architectureProperties));

        assembler = new AgentContextAssembler(
                assetService,
                ragRetrievalService,
                settingsService,
                viewProvider,
                propsProvider,
                workLogRepository);

        PlatformAiSettings settings = mock(PlatformAiSettings.class);
        when(settings.isRagEnabled()).thenReturn(true);
        when(settingsService.getSettings()).thenReturn(settings);

        when(assetService.get(1L)).thenReturn(new AssetResponse(
                1L, "nn-1", AssetKind.SERVER, "10.0.0.1", 22, "{}", null, null, true, true, List.of(),
                Instant.now(), Instant.now()));

        when(workLogRepository.findByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
        when(workLogRepository.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(workLogRepository.findFiltered(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void assemblesAllSlotHeaders() {
        String hugeDump = "FULL_ARCHITECTURE_DUMP_" + "X".repeat(8000);
        ArchitectureViewResponse view = new ArchitectureViewResponse(List.of(), hugeDump);
        when(architectureViewService.buildView(anyList(), anyList())).thenReturn(view);
        when(architectureViewService.toPromptSnippet(view))
                .thenReturn("## Global architecture\ncluster summary\n\n## Active facts\n- [global] ROLE nn is namenode");

        when(ragRetrievalService.retrieve(eq("where is namenode?"), any(RagScope.class)))
                .thenReturn(List.of(new ScoredChunk(
                        1L, KnowledgeSourceType.ARCHITECTURE, 1L, 0, "namenode on nn-1", "{}", 0.91)));

        WorkLog log = new WorkLog();
        log.setLogType("AGENT");
        log.setLevel("L0");
        log.setSummary("checked jps on nn-1");
        when(workLogRepository.findByConversationIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(log));

        UiContext ui = new UiContext("ai", "ai", 1L, List.of(1L));
        String prompt = assembler.assemble(7L, List.of(1L), List.of(), "where is namenode?", 10L, ui);

        assertThat(prompt).contains(AgentContextAssembler.HEADER_IDENTITY);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_TARGETS);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_RAG);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_ARCHITECTURE);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_WORK_LOGS);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_UI);
        assertThat(prompt).contains(AgentContextAssembler.HEADER_SECRETS);
        assertThat(prompt).contains("nn-1");
        assertThat(prompt).contains("namenode on nn-1");
        assertThat(prompt).contains("checked jps");
        assertThat(prompt).contains("surface=ai");
        assertThat(prompt).contains("Description as SSOT");
        verify(architectureViewService).toPromptSnippet(view);
    }

    @Test
    void doesNotIncludeFullArchitectureDumpWhenBodyIsHuge() {
        String hugeDump = "FULL_ARCHITECTURE_DUMP_" + "Y".repeat(12000);
        ArchitectureViewResponse view = new ArchitectureViewResponse(List.of(), hugeDump);
        when(architectureViewService.buildView(anyList(), anyList())).thenReturn(view);
        when(architectureViewService.toPromptSnippet(view))
                .thenReturn("## Active facts\n- [asset:1] ROLE host is namenode");
        when(ragRetrievalService.retrieve(any(), any(RagScope.class))).thenReturn(List.of());

        String prompt = assembler.assemble(1L, List.of(1L), List.of(), "topology?", null, null);

        assertThat(prompt).doesNotContain(hugeDump);
        assertThat(prompt).doesNotContain("FULL_ARCHITECTURE_DUMP_");
        assertThat(prompt).contains("ROLE host is namenode");
        assertThat(prompt).contains(AgentContextAssembler.HEADER_ARCHITECTURE);
    }

    @Test
    void respectsContextMaxChars() {
        architectureProperties.setContextMaxChars(180);
        ArchitectureViewResponse view = new ArchitectureViewResponse(List.of(), "unused-huge");
        when(architectureViewService.buildView(anyList(), anyList())).thenReturn(view);
        when(architectureViewService.toPromptSnippet(view)).thenReturn("facts-snippet");
        when(ragRetrievalService.retrieve(any(), any(RagScope.class)))
                .thenReturn(List.of(new ScoredChunk(
                        2L,
                        KnowledgeSourceType.WORK_LOG,
                        2L,
                        0,
                        "long rag content ".repeat(40),
                        "{}",
                        0.8)));

        String prompt = assembler.assemble(1L, List.of(1L), List.of(), "disk?", null, null);

        assertThat(prompt.length()).isLessThanOrEqualTo(181); // 180 + ellipsis
        assertThat(prompt).endsWith("…");
        assertThat(prompt).contains(AgentContextAssembler.HEADER_IDENTITY);
    }
}
