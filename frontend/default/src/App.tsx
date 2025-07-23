import React from 'react';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from './components/LanguageSwitcher';

function App() {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50">
      <div className="w-full max-w-md p-8 bg-white rounded shadow">
        <h1 className="text-2xl font-bold mb-4 text-center">{t('welcome')}</h1>
        <LanguageSwitcher />
        {/* 这里可扩展注册/审核等功能 */}
      </div>
    </div>
  );
}
export default App; 