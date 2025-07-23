import React from 'react';
import { useTranslation } from 'react-i18next';

const LanguageSwitcher: React.FC = () => {
  const { i18n } = useTranslation();
  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
  };
  return (
    <div className="flex justify-center gap-4 my-4">
      <button
        className="px-3 py-1 rounded border border-gray-300 bg-gray-100 hover:bg-gray-200"
        onClick={() => changeLanguage('zh')}
      >
        中文
      </button>
      <button
        className="px-3 py-1 rounded border border-gray-300 bg-gray-100 hover:bg-gray-200"
        onClick={() => changeLanguage('en')}
      >
        English
      </button>
    </div>
  );
};
export default LanguageSwitcher; 