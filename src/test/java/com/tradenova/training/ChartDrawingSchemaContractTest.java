package com.tradenova.training;

import com.tradenova.training.entity.ChartDrawingType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChartDrawingSchemaContractTest {
    @Test
    void manualMysqlMigrationContainsEveryPersistedDrawingType() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/manual/V7__fix_chart_drawing_type_schema.sql"));

        for (ChartDrawingType type : ChartDrawingType.values()) {
            assertThat(migration).contains("'" + type.name() + "'");
        }
        assertThat(migration).contains("MODIFY COLUMN start_price DECIMAL(19,4) NULL");
    }
}
