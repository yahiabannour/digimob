"""
******************************************************************************
 file:      logging function
 brief:     Software zur Anzeige der Logging-Dateien
 author(s): Tobias Hertzler, Fanuel Bimo Setiadi
 adviser:   Tanja Wysinsky - medical advising
 version:   1.1.9
 date:      10.12.2025
 software:  Python (Version: 3.11.2)
 int. libs:
 ext. libs: OpenCV (Version: 4.10.0.84)
            logging
 hardware:
 info:
******************************************************************************
"""

import logging
import os

class LoggingConfigurator:
    def __init__(self, log_file='Log.log'):
        self.logger = logging.getLogger()
        self.log_file = log_file
        self.logger.handlers.clear()

        if os.path.exists(log_file):
            os.remove(log_file)

    def setLoggingMode(self, loggingMode):
        self.logger.handlers.clear()

        if loggingMode == 0:
            # Modus 0: Deaktiviert das Logging vollständig
            self.logger.addHandler(logging.NullHandler())
        elif loggingMode in range(1, 6):
            # Logging-Level festlegen
            levels = {
                1: logging.DEBUG,
                2: logging.INFO,
                3: logging.WARNING,
                4: logging.ERROR,
                5: logging.CRITICAL
            }
            self.logger.setLevel(levels[loggingMode])
            file_handler = logging.FileHandler(self.log_file)
            formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
            file_handler.setFormatter(formatter)
            self.logger.addHandler(file_handler)
        else:
            raise ValueError("Ungültiger Logging-Modus. Gültig sind 0, 1, 2, 3, 4 oder 5.")
