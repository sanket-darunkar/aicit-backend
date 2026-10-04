-- ============================================================
-- AICIT Platform – V4: Courses
-- ============================================================
CREATE TABLE IF NOT EXISTS courses (
    id               BIGSERIAL    NOT NULL,
    name             VARCHAR(255) NOT NULL,
    code             VARCHAR(50)  NOT NULL,
    description      TEXT,
    duration_months  INTEGER,
    category         VARCHAR(100),
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_courses   PRIMARY KEY (id),
    CONSTRAINT uq_course_code UNIQUE (code)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_course_code     ON courses (code);
CREATE INDEX        IF NOT EXISTS idx_course_active   ON courses (is_active);
CREATE INDEX        IF NOT EXISTS idx_course_category ON courses (category);

-- Seed default courses
INSERT INTO courses (name, code, description, duration_months, category, is_active, created_at, updated_at) VALUES
('Diploma in Computer Application',              'DCA',        'Comprehensive diploma covering computer fundamentals, MS Office, internet, and basic programming.', 6,  'Computer & IT',       TRUE, NOW(), NOW()),
('MS-CIT',                                       'MSCIT',      'Maharashtra State Certificate in Information Technology.', 3, 'Computer & IT',       TRUE, NOW(), NOW()),
('Course on Computer Concepts (CCC)',            'CCC',        'Government of India – NIELIT computer concepts course.', 3, 'Government Exams',    TRUE, NOW(), NOW()),
('Tally Prime with GST',                         'TALLY',      'Tally Prime accounting software with GST billing.', 2, 'Accounting & Finance', TRUE, NOW(), NOW()),
('Advanced Tally Prime with GST',                'TALLY-ADV',  'Advanced Tally Prime with GST accounting.', 2, 'Accounting & Finance', TRUE, NOW(), NOW()),
('Advanced Excel',                               'ADV-EXCEL',  'Excel formulas, charts, data analysis.', 2, 'Accounting & Finance', TRUE, NOW(), NOW()),
('Graphic Designing',                            'GD',         'Professional graphic design – posters, logos, social media.', 3, 'Design & Media',     TRUE, NOW(), NOW()),
('KLiC Video Editing',                           'VIDEO-EDIT', 'Video editing for reels, YouTube, professional content.', 2, 'Design & Media',     TRUE, NOW(), NOW()),
('KLiC Web Designing',                           'WEB-DESIGN', 'HTML, CSS, and web tools for professional websites.', 3, 'Computer & IT',       TRUE, NOW(), NOW()),
('Certificate in Hardware & Networking',         'HW-NET',     'Computer hardware, assembly, troubleshooting and networking basics.', 5, 'Computer & IT', TRUE, NOW(), NOW()),
('English Typing – 30 W.P.M.',                  'ENG-TYP-30', 'Computer-based English typing at 30 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('English Typing – 40 W.P.M.',                  'ENG-TYP-40', 'Computer-based English typing at 40 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('Marathi Typing – 30 W.P.M.',                  'MAR-TYP-30', 'Computer-based Marathi typing at 30 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('Marathi Typing – 40 W.P.M.',                  'MAR-TYP-40', 'Computer-based Marathi typing at 40 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('Hindi Typing – 30 W.P.M.',                    'HIN-TYP-30', 'Computer-based Hindi typing at 30 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('Hindi Typing – 40 W.P.M.',                    'HIN-TYP-40', 'Computer-based Hindi typing at 40 words per minute.', 3, 'Typing',             TRUE, NOW(), NOW()),
('Office Assistance',                            'OFFICE-AST', 'MS Office, email, documentation for office work.', 3, 'Accounting & Finance', TRUE, NOW(), NOW()),
('Google Workspace Expert',                      'GWS',        'Gmail, Google Docs, Sheets, Drive – expert level.', 2, 'Computer & IT',       TRUE, NOW(), NOW()),
('Certificate in Desktop Publishing',            'DTP-CERT',   'Print design, layout and publishing software.', 3, 'Design & Media',     TRUE, NOW(), NOW()),
('Diploma in Desktop Publishing',                'DTP-DIP',    'Advanced print design and layout diploma.', 3, 'Design & Media',     TRUE, NOW(), NOW()),
('GCC-TBC Pune Board Typing',                    'GCC-TBC',    'GCC-TBC Pune Board typing certification program.', 6, 'Typing',             TRUE, NOW(), NOW()),
('MS-CIT + English Typing 30 & 40 WPM + Diploma','COMBO-1',   'MS-CIT, English Typing 30 & 40 WPM, and Diploma – 4 certificates.', 6, 'Computer & IT', TRUE, NOW(), NOW()),
('MS-CIT + Typing + Tally Prime + Diploma',      'COMBO-2',   'Comprehensive career program – MS-CIT, Typing, Tally Prime, Diploma.', 8, 'Computer & IT', TRUE, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;
