package com.tradenova.report.controller;

import com.tradenova.report.service.ReportDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportDocumentController {

    private final ReportDocumentService reportDocumentService;



}
