package com.example.jkword.model

object DocumentTemplates {

    data class TemplateInfo(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val factory: () -> DocumentModel
    )

    val ALL_TEMPLATES = listOf(
        TemplateInfo(
            id = "blank",
            name = "Blank Document",
            description = "Start with a clean page and custom styling",
            category = "Basic",
            factory = { createBlankDocument() }
        ),
        TemplateInfo(
            id = "resume",
            name = "Resume / CV",
            description = "Clean modern executive resume with skills & experience",
            category = "Career",
            factory = { createResumeTemplate() }
        ),
        TemplateInfo(
            id = "business_report",
            name = "Business Report",
            description = "Executive report with headings, summary table, and metrics",
            category = "Business",
            factory = { createBusinessReportTemplate() }
        ),
        TemplateInfo(
            id = "professional_letter",
            name = "Professional Letter",
            description = "Formal business correspondence with sender and recipient headers",
            category = "Letters",
            factory = { createProfessionalLetterTemplate() }
        ),
        TemplateInfo(
            id = "student_assignment",
            name = "Student Assignment",
            description = "Academic paper with course title, abstract, and bibliography",
            category = "Education",
            factory = { createStudentAssignmentTemplate() }
        ),
        TemplateInfo(
            id = "meeting_notes",
            name = "Meeting Notes",
            description = "Structured agenda, attendees list, key takeaways, and action items",
            category = "Business",
            factory = { createMeetingNotesTemplate() }
        ),
        TemplateInfo(
            id = "cover_letter",
            name = "Cover Letter",
            description = "Targeted job application letter showcasing your qualifications",
            category = "Career",
            factory = { createCoverLetterTemplate() }
        ),
        TemplateInfo(
            id = "simple_invoice",
            name = "Simple Invoice",
            description = "Billing statement with line items, tax, and total summary table",
            category = "Finance",
            factory = { createInvoiceTemplate() }
        ),
        TemplateInfo(
            id = "project_proposal",
            name = "Project Proposal",
            description = "Comprehensive project scope, objectives, timeline, and deliverables",
            category = "Planning",
            factory = { createProjectProposalTemplate() }
        ),
        TemplateInfo(
            id = "research_paper",
            name = "Research Paper",
            description = "Scholarly document layout with methodology, analysis, and citations",
            category = "Education",
            factory = { createResearchPaperTemplate() }
        ),
        TemplateInfo(
            id = "multilingual_urdu",
            name = "Urdu & English Document",
            description = "Bilingual document demonstrating Urdu Nastaliq/Naskh RTL & English LTR",
            category = "Multilingual",
            factory = { createMultilingualTemplate() }
        )
    )

    fun createBlankDocument(title: String = "Untitled Document"): DocumentModel {
        return DocumentModel(
            title = title,
            blocks = listOf(
                ParagraphBlock(
                    runs = listOf(TextRun("")),
                    style = ParagraphStyle.NORMAL
                )
            )
        )
    }

    fun createResumeTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("JAHANGIR KHAN", isBold = true, fontSizePt = 24f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE,
                alignment = BlockAlignment.CENTER
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Senior Software Engineer | Mobile & Cloud Systems\njahangir@example.com • +1 (555) 019-2834 • San Francisco, CA", fontSizePt = 10f, fontColorHex = "#555555")),
                style = ParagraphStyle.SUBTITLE,
                alignment = BlockAlignment.CENTER
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("PROFESSIONAL SUMMARY", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Results-driven mobile engineer with 8+ years developing mission-critical native Android applications and high-performance document systems. Proven track record of architecting scalable offline-first applications with intuitive user experiences.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("CORE COMPETENCIES", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            TableBlock(
                rows = 2,
                cols = 2,
                cells = listOf(
                    listOf(
                        TableCell(text = "Android Jetpack, Compose, Kotlin, Coroutines", isHeader = true, backgroundColorHex = "#E8F0FE"),
                        TableCell(text = "OOXML/DOCX, PDF Rendering, File Systems", isHeader = true, backgroundColorHex = "#E8F0FE")
                    ),
                    listOf(
                        TableCell(text = "Room SQLite, Offline Sync, SAF Framework", isHeader = false),
                        TableCell(text = "Clean MVVM Architecture, M3 Design Systems", isHeader = false)
                    )
                )
            ),
            ParagraphBlock(
                runs = listOf(TextRun("WORK EXPERIENCE", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(
                    TextRun("Lead Android Architect — Apex Technologies", isBold = true, fontSizePt = 12f),
                    TextRun(" (2022 – Present)\n", isItalic = true, fontSizePt = 10f, fontColorHex = "#666666"),
                    TextRun("• Engineered high-speed native word processor serving 500k+ active daily users.\n• Reduced memory consumption by 45% using streaming OOXML zip processing.\n• Mentored engineering team on Compose performance and reactive patterns.")
                ),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("EDUCATION", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Bachelor of Science in Computer Science — Stanford University (2018)", fontSizePt = 11f)),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Resume - Jahangir Khan", blocks = blocks)
    }

    fun createBusinessReportTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("QUARTERLY BUSINESS REVIEW", isBold = true, fontSizePt = 22f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.TITLE
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Executive Operations Summary • Q3 Performance", fontSizePt = 12f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("1. Executive Summary", isBold = true, fontSizePt = 15f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("During the third quarter, the organization achieved steady operational growth, outperforming key performance targets across user engagement and product delivery.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("2. Key Performance Indicators", isBold = true, fontSizePt = 15f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.HEADING_1
            ),
            TableBlock(
                rows = 4,
                cols = 3,
                cells = listOf(
                    listOf(
                        TableCell(text = "Metric", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Target", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Actual", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true)
                    ),
                    listOf(
                        TableCell(text = "Active Users"),
                        TableCell(text = "100,000"),
                        TableCell(text = "128,400 (+28%)")
                    ),
                    listOf(
                        TableCell(text = "Document Retention"),
                        TableCell(text = "85%"),
                        TableCell(text = "92% (+7%)")
                    ),
                    listOf(
                        TableCell(text = "Avg Edit Latency"),
                        TableCell(text = "< 16 ms"),
                        TableCell(text = "8.2 ms")
                    )
                )
            ),
            ParagraphBlock(
                runs = listOf(TextRun("3. Next Quarter Objectives", isBold = true, fontSizePt = 15f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("• Expand local file format interoperability with DOCX and PDF export.\n• Enhance RTL document shaping for Arabic and Urdu typography.\n• Introduce advanced table cell styling and border customization.")),
                style = ParagraphStyle.NORMAL,
                listType = ListType.BULLET
            )
        )
        return DocumentModel(title = "Business Report Q3", blocks = blocks)
    }

    fun createProfessionalLetterTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("Jahangir Khan\n100 Innovation Way\nSan Francisco, CA 94105\nDate: October 11, 2026", fontSizePt = 10f, fontColorHex = "#555555")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Hiring Committee\nPremier Global Enterprises\n450 Tech Plaza\nSeattle, WA 98101", fontSizePt = 10f)),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Subject: Application for Staff Mobile Systems Engineer", isBold = true, fontSizePt = 12f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Dear Members of the Selection Committee,\n\nI am writing to express my strong enthusiasm for the Staff Mobile Systems Engineer role. With extensive engineering experience delivering reliable native Android solutions, I am excited about the opportunity to contribute to your core productivity suite.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Throughout my career, I have prioritized user privacy, offline resilience, and robust document architecture. My work on word processing engines with OOXML format compatibility directly aligns with your team's goals.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Thank you for your time and consideration. I welcome the opportunity to discuss how my technical expertise can support your initiatives.\n\nSincerely,\nJahangir Khan")),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Professional Letter", blocks = blocks)
    }

    fun createStudentAssignmentTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("COMPUTER SCIENCE & SOFTWARE SYSTEMS", isBold = true, fontSizePt = 11f, fontColorHex = "#666666")),
                style = ParagraphStyle.NORMAL,
                alignment = BlockAlignment.CENTER
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Architectural Design of Mobile Word Processors", isBold = true, fontSizePt = 20f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE,
                alignment = BlockAlignment.CENTER
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Course: CS 420 • Distributed & Mobile Systems\nAuthor: Jahangir Khan • Student ID: 2026-9041", fontSizePt = 10f)),
                style = ParagraphStyle.SUBTITLE,
                alignment = BlockAlignment.CENTER
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("Abstract", isBold = true, fontSizePt = 13f)),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Mobile word processing requires balancing low memory footprints with high-fidelity document formatting. This paper reviews OOXML package parsing, responsive text layout algorithms, and local crash recovery mechanisms in resource-constrained environments.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("1. Introduction", isBold = true, fontSizePt = 14f)),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Modern mobile productivity demands desktop-grade word processing without relying on constant cloud connectivity. We examine how local document engines parse ZIP-based OOXML containers and translate them into native Jetpack Compose UI trees.")),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Student Assignment - Mobile Systems", blocks = blocks)
    }

    fun createMeetingNotesTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("PROJECT SYNC & ARCHITECTURE REVIEW", isBold = true, fontSizePt = 20f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Date: October 11, 2026 • Time: 10:00 AM PST • Facilitator: Jahangir Khan", fontSizePt = 10f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("Attendees", isBold = true, fontSizePt = 13f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("• Jahangir Khan (Lead)\n• Sarah Lin (Core UI)\n• David Ortiz (Document Engine)\n• Amara Vance (QA)")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Key Decisions", isBold = true, fontSizePt = 13f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("1. Adopt local Room persistence for metadata with debounced file writes.\n2. Enable native PDF export using Android's PdfDocument rendering pipeline.\n3. Integrate bi-directional layout support for English and Urdu scripts.")),
                style = ParagraphStyle.NORMAL,
                listType = ListType.NUMBERED
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Action Items", isBold = true, fontSizePt = 13f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_2
            ),
            TableBlock(
                rows = 3,
                cols = 3,
                cells = listOf(
                    listOf(
                        TableCell(text = "Task", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Owner", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Due Date", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true)
                    ),
                    listOf(
                        TableCell(text = "DOCX Zip Writer validation"),
                        TableCell(text = "Jahangir"),
                        TableCell(text = "Wednesday")
                    ),
                    listOf(
                        TableCell(text = "Table styling controls"),
                        TableCell(text = "Sarah"),
                        TableCell(text = "Friday")
                    )
                )
            )
        )
        return DocumentModel(title = "Meeting Notes - Project Sync", blocks = blocks)
    }

    fun createCoverLetterTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("Jahangir Khan", isBold = true, fontSizePt = 18f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE
            ),
            ParagraphBlock(
                runs = listOf(TextRun("jahangir@example.com • (555) 019-2834 • San Francisco, CA", fontSizePt = 10f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("To the Hiring Team,\n\nI am writing to express my strong interest in joining your team as a Senior Android Engineer. Having led the development of comprehensive native productivity tools, I am eager to bring my passion for craftsmanship to your organization.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("My technical background encompasses deep knowledge of Kotlin coroutines, custom canvas rendering, OOXML file structures, and clean modular architecture. I look forward to contributing to your vision.\n\nWarm regards,\nJahangir Khan")),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Cover Letter", blocks = blocks)
    }

    fun createInvoiceTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("INVOICE #INV-2026-084", isBold = true, fontSizePt = 22f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.TITLE
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Issued: October 11, 2026 • Due: November 10, 2026", fontSizePt = 10f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE
            ),
            HorizontalRuleBlock(),
            TableBlock(
                rows = 4,
                cols = 4,
                cells = listOf(
                    listOf(
                        TableCell(text = "Item / Description", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Hours", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Rate", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true),
                        TableCell(text = "Amount", isHeader = true, backgroundColorHex = "#E8F0FE", isBold = true)
                    ),
                    listOf(
                        TableCell(text = "Mobile App UI/UX Development"),
                        TableCell(text = "40"),
                        TableCell(text = "$95/hr"),
                        TableCell(text = "$3,800.00")
                    ),
                    listOf(
                        TableCell(text = "DOCX Engine & Storage Integration"),
                        TableCell(text = "35"),
                        TableCell(text = "$95/hr"),
                        TableCell(text = "$3,325.00")
                    ),
                    listOf(
                        TableCell(text = "Total Due (USD)", isBold = true, backgroundColorHex = "#FFF9C4"),
                        TableCell(text = "75 hrs", isBold = true, backgroundColorHex = "#FFF9C4"),
                        TableCell(text = "-", isBold = true, backgroundColorHex = "#FFF9C4"),
                        TableCell(text = "$7,125.00", isBold = true, backgroundColorHex = "#FFF9C4")
                    )
                )
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Payment Terms: Net 30 days. Wire transfer or direct deposit to account on file.\nThank you for your business!")),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Invoice INV-2026-084", blocks = blocks)
    }

    fun createProjectProposalTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("PROJECT PROPOSAL: JK WORD MOBILE SUITE", isBold = true, fontSizePt = 20f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Prepared by: JK Engineering Solutions • October 2026", fontSizePt = 11f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("1. Objective", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("To develop a premier offline-first mobile word processing suite that provides full DOCX and PDF compatibility, rich text formatting, table manipulation, and multi-language support.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("2. Scope of Work", isBold = true, fontSizePt = 14f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("• Comprehensive Rich-Text Editor Engine with Undo/Redo.\n• Standard OOXML DOCX ZIP parsing and generation.\n• Native PDF Export with proper page margins and page numbers.\n• Room persistence with instant search, starred items, and trash recovery.")),
                style = ParagraphStyle.NORMAL,
                listType = ListType.BULLET
            )
        )
        return DocumentModel(title = "Project Proposal", blocks = blocks)
    }

    fun createResearchPaperTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("Deterministic Document Layout in Mobile Android Environments", isBold = true, fontSizePt = 18f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.TITLE,
                alignment = BlockAlignment.CENTER
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Jahangir Khan\nDepartment of Mobile Computing\nResearch Paper Series 2026", fontSizePt = 10f, fontColorHex = "#555555")),
                style = ParagraphStyle.SUBTITLE,
                alignment = BlockAlignment.CENTER
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("Abstract", isBold = true, fontSizePt = 12f)),
                style = ParagraphStyle.HEADING_2
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Achieving desktop-grade word processing fidelity on mobile operating systems poses significant memory and layout hurdles. This study details an approach for non-blocking pagination and OOXML round-tripping on Android devices.")),
                style = ParagraphStyle.NORMAL
            ),
            ParagraphBlock(
                runs = listOf(TextRun("1. Problem Statement", isBold = true, fontSizePt = 13f)),
                style = ParagraphStyle.HEADING_1
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Standard mobile text inputs lack structured document models for tables, complex character formatting, and deterministic pagination. Our architecture abstracts document blocks into a persistent model independent of UI rendering.")),
                style = ParagraphStyle.NORMAL
            )
        )
        return DocumentModel(title = "Research Paper", blocks = blocks)
    }

    fun createMultilingualTemplate(): DocumentModel {
        val blocks = listOf(
            ParagraphBlock(
                runs = listOf(TextRun("اردو اور انگریزی دستاویز — JK word", isBold = true, fontSizePt = 20f, fontColorHex = "#185ABD")),
                style = ParagraphStyle.TITLE,
                alignment = BlockAlignment.CENTER
            ),
            ParagraphBlock(
                runs = listOf(TextRun("Bilingual Word Processing Demonstration • Urdu Nastaliq & English LTR", fontSizePt = 11f, fontColorHex = "#666666")),
                style = ParagraphStyle.SUBTITLE,
                alignment = BlockAlignment.CENTER
            ),
            HorizontalRuleBlock(),
            ParagraphBlock(
                runs = listOf(TextRun("خوش آمدید (Welcome)", isBold = true, fontSizePt = 16f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.HEADING_1,
                alignment = BlockAlignment.RIGHT
            ),
            ParagraphBlock(
                runs = listOf(TextRun("یہ ایک جدید اور طاقتور ورڈ پروسیسر ہے جو اردو اور انگریزی دونوں زبانوں کو بخوبی سپورٹ کرتا ہے۔ آپ اپنی دستاویزات کو آف لائن بنا سکتے ہیں اور محفوظ کر سکتے ہیں۔")),
                style = ParagraphStyle.NORMAL,
                alignment = BlockAlignment.RIGHT
            ),
            ParagraphBlock(
                runs = listOf(TextRun("English Section", isBold = true, fontSizePt = 14f, fontColorHex = "#0F4C81")),
                style = ParagraphStyle.HEADING_2,
                alignment = BlockAlignment.LEFT
            ),
            ParagraphBlock(
                runs = listOf(TextRun("JK word provides complete bidirectional text processing, ensuring both Right-to-Left (RTL) scripts like Urdu and Arabic, and Left-to-Right (LTR) scripts like English and French render cleanly in the same document.")),
                style = ParagraphStyle.NORMAL,
                alignment = BlockAlignment.LEFT
            )
        )
        return DocumentModel(title = "Bilingual Document (Urdu & English)", blocks = blocks)
    }
}
