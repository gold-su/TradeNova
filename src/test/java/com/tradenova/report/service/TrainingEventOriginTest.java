package com.tradenova.report.service;

import com.tradenova.report.entity.EventOrigin;
import com.tradenova.report.entity.TrainingEvent;
import com.tradenova.report.entity.Type;
import com.tradenova.report.repository.TrainingEventRepository;
import com.tradenova.training.entity.TrainingSessionChart;
import com.tradenova.training.repository.TrainingSessionChartRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingEventOriginTest {
    @Test
    void recordsPublicNoteAsUserAuthoredAndBackendAppendAsSystem() {
        TrainingEventRepository repository = mock(TrainingEventRepository.class);
        TrainingSessionChartRepository chartRepository = mock(TrainingSessionChartRepository.class);
        TrainingEventService service = new TrainingEventService(repository, chartRepository);
        when(chartRepository.findByIdAndSession_User_Id(10L, 1L))
                .thenReturn(Optional.of(TrainingSessionChart.builder().id(10L).build()));
        when(repository.save(any(TrainingEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.appendUserAuthored(1L, 10L, Type.NOTE, "my note", null);
        service.append(1L, 10L, Type.PROGRESS, "advanced", null);

        ArgumentCaptor<TrainingEvent> captor = ArgumentCaptor.forClass(TrainingEvent.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(EventOrigin.USER, captor.getAllValues().get(0).getOrigin());
        assertEquals(EventOrigin.SYSTEM, captor.getAllValues().get(1).getOrigin());
    }
}
