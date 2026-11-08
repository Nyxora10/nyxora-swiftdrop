'use client';

import React, { useState } from 'react';

export default function CourierShiftToggle() {
  const [isOnDuty, setIsOnDuty] = useState(false);

  const toggleShift = () => {
    setIsOnDuty(!isOnDuty);
  };

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs font-medium text-slate-600 hidden sm:inline">
        Shift: <strong className={isOnDuty ? "text-emerald-600" : "text-slate-400"}>
          {isOnDuty ? 'ON DUTY' : 'OFF DUTY'}
        </strong>
      </span>
      <button
        type="button"
        onClick={toggleShift}
        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
          isOnDuty ? 'bg-emerald-500' : 'bg-slate-300'
        }`}
        role="switch"
        aria-checked={isOnDuty}
        title="Toggle Courier Shift Availability"
      >
        <span
          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
            isOnDuty ? 'translate-x-4' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}
