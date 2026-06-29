package com.example.data

object DefaultTimetable {
    val PRE_POPULATED_PERIODS = listOf(
        // Monday (MON)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "MON",
            subjectCode = "A55091",
            subjectName = "NSO / NSS / OUTREACH / CLUB ACTIVITIES",
            facultyName = "Coordinators",
            roomNumber = "Dept. Hall",
            startTime = "09:00 AM",
            endTime = "12:40 PM",
            startMinutes = 540,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "MON",
            subjectCode = "SPORTS",
            subjectName = "Sports Session",
            facultyName = "Physical Educators",
            roomNumber = "Sports Ground",
            startTime = "01:20 PM",
            endTime = "04:05 PM",
            startMinutes = 800,
            endMinutes = 965
        ),

        // Tuesday (TUE)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "TUE",
            subjectCode = "EMA3180",
            subjectName = "Deep Learning (DL)",
            facultyName = "Mr. T. Bala Krishna",
            roomNumber = "I-501",
            startTime = "09:00 AM",
            endTime = "09:55 AM",
            startMinutes = 540,
            endMinutes = 595
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "TUE",
            subjectCode = "EMA3182",
            subjectName = "Foundations of Operating Systems (FOS)",
            facultyName = "Ms. T. Neetha",
            roomNumber = "I-501",
            startTime = "09:55 AM",
            endTime = "10:50 AM",
            startMinutes = 595,
            endMinutes = 650
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "TUE",
            subjectCode = "EMA3181",
            subjectName = "Essentials of Web Programming (EWP)",
            facultyName = "Dr. A. Udaya Kumar",
            roomNumber = "I-501",
            startTime = "10:50 AM",
            endTime = "11:45 AM",
            startMinutes = 650,
            endMinutes = 705
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "TUE",
            subjectCode = "COUN",
            subjectName = "Counseling",
            facultyName = "Department Advisors",
            roomNumber = "I-501",
            startTime = "11:45 AM",
            endTime = "12:40 PM",
            startMinutes = 705,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "TUE",
            subjectCode = "EMA3180P",
            subjectName = "Deep Learning Lab (DL LAB)",
            facultyName = "Mr. T. Bala Krishna",
            roomNumber = "I-410",
            startTime = "01:20 PM",
            endTime = "04:05 PM",
            startMinutes = 800,
            endMinutes = 965,
            isLab = true
        ),

        // Wednesday (WED)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "WED",
            subjectCode = "EMA3182",
            subjectName = "Foundations of Operating Systems (FOS)",
            facultyName = "Ms. T. Neetha",
            roomNumber = "I-501",
            startTime = "09:00 AM",
            endTime = "09:55 AM",
            startMinutes = 540,
            endMinutes = 595
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "WED",
            subjectCode = "ESE3101",
            subjectName = "Problem Solving in Engineering-I (IP3)",
            facultyName = "Integrated Project Faculty",
            roomNumber = "I-506",
            startTime = "09:55 AM",
            endTime = "12:40 PM",
            startMinutes = 595,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "WED",
            subjectCode = "EMA3181P",
            subjectName = "Essentials of Web Programming Lab (EWP LAB)",
            facultyName = "Dr. A. Udaya Kumar",
            roomNumber = "I-411",
            startTime = "01:20 PM",
            endTime = "04:05 PM",
            startMinutes = 800,
            endMinutes = 965,
            isLab = true
        ),

        // Thursday (THU)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "THU",
            subjectCode = "EMA3180",
            subjectName = "Deep Learning (DL)",
            facultyName = "Mr. T. Bala Krishna",
            roomNumber = "I-501",
            startTime = "09:00 AM",
            endTime = "09:55 AM",
            startMinutes = 540,
            endMinutes = 595
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "THU",
            subjectCode = "EMA3181",
            subjectName = "Essentials of Web Programming (EWP)",
            facultyName = "Dr. A. Udaya Kumar",
            roomNumber = "I-501",
            startTime = "09:55 AM",
            endTime = "10:50 AM",
            startMinutes = 595,
            endMinutes = 650
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "THU",
            subjectCode = "EMA3181",
            subjectName = "Essentials of Web Programming (EWP)",
            facultyName = "Dr. A. Udaya Kumar",
            roomNumber = "I-501",
            startTime = "10:50 AM",
            endTime = "11:45 AM",
            startMinutes = 650,
            endMinutes = 705
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "THU",
            subjectCode = "EMA3182",
            subjectName = "Foundations of Operating Systems (FOS)",
            facultyName = "Ms. T. Neetha",
            roomNumber = "I-501",
            startTime = "11:45 AM",
            endTime = "12:40 PM",
            startMinutes = 705,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "THU",
            subjectCode = "EMI3105",
            subjectName = "Quantitative Aptitude and Logical Reasoning-II (QALR-II)",
            facultyName = "QA Faculty Team",
            roomNumber = "I-501",
            startTime = "01:20 PM",
            endTime = "04:05 PM",
            startMinutes = 800,
            endMinutes = 965
        ),

        // Friday (FRI)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "EMA3182",
            subjectName = "Foundations of Operating Systems (FOS)",
            facultyName = "Ms. T. Neetha",
            roomNumber = "I-501",
            startTime = "09:00 AM",
            endTime = "09:55 AM",
            startMinutes = 540,
            endMinutes = 595
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "EMA3180",
            subjectName = "Deep Learning (DL)",
            facultyName = "Mr. T. Bala Krishna",
            roomNumber = "I-501",
            startTime = "09:55 AM",
            endTime = "10:50 AM",
            startMinutes = 595,
            endMinutes = 650
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "EMA3180",
            subjectName = "Deep Learning (DL)",
            facultyName = "Mr. T. Bala Krishna",
            roomNumber = "I-501",
            startTime = "10:50 AM",
            endTime = "11:45 AM",
            startMinutes = 650,
            endMinutes = 705
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "LIB",
            subjectName = "Library",
            facultyName = "Department Librarian",
            roomNumber = "I-501",
            startTime = "11:45 AM",
            endTime = "12:40 PM",
            startMinutes = 705,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "EMA3181",
            subjectName = "Essentials of Web Programming (EWP)",
            facultyName = "Dr. A. Udaya Kumar",
            roomNumber = "I-501",
            startTime = "01:20 PM",
            endTime = "02:15 PM",
            startMinutes = 800,
            endMinutes = 855
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "FRI",
            subjectCode = "ESI3X01",
            subjectName = "Open Elective-I (OEC-I)",
            facultyName = "Open Elective Faculty",
            roomNumber = "I-501",
            startTime = "02:15 PM",
            endTime = "04:05 PM",
            startMinutes = 855,
            endMinutes = 965
        ),

        // Saturday (SAT)
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "SAT",
            subjectCode = "EMA3151",
            subjectName = "Full Stack Dev / Competitive Programming (TT)",
            facultyName = "Technical Trainers",
            roomNumber = "I-501",
            startTime = "09:00 AM",
            endTime = "12:40 PM",
            startMinutes = 540,
            endMinutes = 760
        ),
        PeriodEntity(
            scheduleOwnerName = "My Schedule",
            dayOfWeek = "SAT",
            subjectCode = "EMA3152",
            subjectName = "Full Stack Dev / Competitive Programming (TT)",
            facultyName = "Technical Trainers",
            roomNumber = "I-501",
            startTime = "01:20 PM",
            endTime = "04:05 PM",
            startMinutes = 800,
            endMinutes = 965
        )
    )
}
